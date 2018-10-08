package secondbid;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

/**
 * 
 * @author Eirini Mitsopoulou
 * @author Kyriaki-Nektaria Pantelidou
 * @author Konstantina Papadopoulou
 */

/**
 * This class creates a JADE agent who performs an auctioneer of a second-price sealed-bid auction.
 */
public class Auctioneer extends Agent {

    // The catalogue of items for sale -> it maps the name of an item to its price
    private Hashtable catalogue;
    
    // The catalogue of bids for each item
    public HashMap<String,ArrayList<Integer>> priceList ;
	
    // The GUI through which the user can add items in the catalogue
    private AuctioneerGUI myGUI;

    // It is true if the auction has begun
    private boolean auctionBegan = false;

    // The template to receive replies
    public MessageTemplate mt; 

    // The list of bidders
    public AID[] bidders;

    // The bidder who provides the best offer
    public AID bestBidder;

    // The best offered price currently
    public int bestPrice;

    // It is true if there are bidders found
    public boolean biddersFound = false;
    
    // It returns true if there is CFP sent
    //CFP is a constant identifying the FIPA( Foundation for Intelligent Physical Agents ) performative
    public boolean CFPSent = false;
    
    //It is true if there are bids received
    public boolean bidsReceived = false;

    public FindBidder p = null;
    public SendCFP q = null;
    
    public ReceiveBids r = null;
    public AnnounceWinner2 s = null;

    /**
    *    Set-up the auctioneer and the auction system.
    */
    @Override
    protected void setup() {
        priceList= new HashMap<String,ArrayList<Integer>>();
                            
        // show a starting message
	System.out.println("The auctions starts!  Auctioneer "+getAID().getName()+" is ready.");

	// Create the catalogue
	catalogue = new Hashtable();

	// Create and show the GUI window
	myGUI = new AuctioneerGUI(this);
	myGUI.showGui();
       
        // Add a TickerBehaviour that schedules a request to bidders every minute
        addBehaviour(new ActionPerMinute(this));
    }

    /**
    *    Agent clean-up operations.
    */
    protected void takeDown() {
	// Close the GUI
	myGUI.dispose();

	// Show a dismissal message
	System.out.println("Auctioneer "+getAID().getName()+" terminating.");
    }

    /**
    *   This is invoked by the GUI when the user adds a new item for sale.
    */
    public void updateCatalogue(final String title, final int price) {
        addBehaviour(
            new OneShotBehaviour() {
                public void action() {
                    ArrayList<Integer> value;
                    catalogue.put(title, new Integer(price));
                    System.out.println(title+" is inserted into catalogue. Initial Price = "+price+ ".");
                    if(priceList.containsKey(title)){
                        value = priceList.get(title);                                              
                    }
                    else{
                        value = new ArrayList<>();
                    }

                    priceList.put(title, value);

                }                
            }             
        );
    }

    /**
    *   This is invoked to delete item.
    */
    public Integer removeItemFromCatalogue(final String title) {
        Integer price = null;
        price = (Integer) catalogue.get(title);
	addBehaviour(
            new OneShotBehaviour() {
                public void action() {
                    catalogue.remove(title);
                }
            }
        );
        priceList.remove(title);
        return price;
    }

    /**
    *    It returns true if the catalogue is empty.
    */
    public boolean isCatalogueEmpty() {
        return catalogue.isEmpty();
    }

    /**
    *    It returns the name of the first item.
    */
    public String getFirstItemName() {
        return (String)catalogue.keySet().toArray()[0];
    }
    
    /**
    *   It returns the initial price of a specific item.
    */
    public int getItemInitialPrice(final String title) {
        Integer price = (Integer) catalogue.get(title);
        if (price != null) {
            return (int)price;
        }
        else {
            return 0;
        }
    }

}

/**
 *  Add a TickerBehaviour that schedules a request to bidders every minute.
*/
class ActionPerMinute extends TickerBehaviour {

    private Auctioneer myAgent;

    private String currentItemName;

    public ActionPerMinute(Auctioneer agent) {
        super(agent, 10000);
        myAgent = agent;
    }

    @Override
    protected void onTick(){

        // Initialize all conditions
        myAgent.biddersFound = false;
        myAgent.CFPSent = false;
        myAgent.bidsReceived = false;
        
        // If there is any item to sell
        if (!myAgent.isCatalogueEmpty()) {

            if (myAgent.p != null) myAgent.removeBehaviour(myAgent.p);
            if (myAgent.q != null) myAgent.removeBehaviour(myAgent.q);
            if (myAgent.r != null) myAgent.removeBehaviour(myAgent.r);
            if (myAgent.s != null) myAgent.removeBehaviour(myAgent.s);

            currentItemName = myAgent.getFirstItemName();
            System.out.println("Starting auction for item " + currentItemName+ ".");
            System.out.println("Waiting for bidders...");

            // Find Bidder
            myAgent.p = new FindBidder(myAgent);
            myAgent.addBehaviour(myAgent.p);
                    
            // Send CFP to all bidders
            myAgent.q = new SendCFP(myAgent, currentItemName, myAgent.getItemInitialPrice(currentItemName));
            myAgent.addBehaviour(myAgent.q);

            // Receive all proposals/refusals from bidders and find the highest bidder
            myAgent.r = new ReceiveBids(myAgent);
            myAgent.addBehaviour(myAgent.r);
            
            // Send the request order to the bidder that provided the best offer
            myAgent.s = new AnnounceWinner2(myAgent, currentItemName);
            myAgent.addBehaviour(myAgent.s);
        }        
        else {
			System.out.println("Add an item before we can commence auctions.");
        }
    }
}
/**
 *  It finds the bidders if any. 
*/
class FindBidder extends Behaviour {

    private Auctioneer myAgent;

    private boolean noBidder = false;
    private boolean oneBidder = false;

    public FindBidder(Auctioneer agent) {
        super(agent);
        myAgent = agent;
    }
    
    public void action() {

        if (!myAgent.biddersFound) {

            // Update the list of bidders
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("blind-auction");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template); 
                if (result.length > 0) {
                    System.out.println("Found the following " + result.length +" bidders:");
                    myAgent.bidders = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        myAgent.bidders[i] = result[i].getName();
                        System.out.println(myAgent.bidders[i].getName());
                    }
                    myAgent.biddersFound = true;                    
                }
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }

        }
    }

    public boolean done() {
        return myAgent.biddersFound;
    }
}

/**
*   Send CFP to all bidders.
*/
class SendCFP extends Behaviour {

    private Auctioneer myAgent;
    private String itemName;
    private int itemInitialPrice;

    public SendCFP(Auctioneer agent, String itemName, int itemInitialPrice) {
        super(agent);
        myAgent = agent;
        this.itemName = itemName;
        this.itemInitialPrice = itemInitialPrice;
    }
    
    public void action() {

        if (!myAgent.CFPSent && myAgent.biddersFound) {

            // Send the cfp to all bidders
            System.out.println("Sending CFP to all bidders..");
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            for (int i = 0; i < myAgent.bidders.length; ++i) {
                cfp.addReceiver(myAgent.bidders[i]);
            } 
            cfp.setContent(this.itemName + "," + this.itemInitialPrice);
            cfp.setConversationId("blind-bid");
            cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
            myAgent.send(cfp);

            // Prepare message template
            myAgent.mt = MessageTemplate.and(MessageTemplate.MatchConversationId("blind-bid"),
                                             MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
        
            myAgent.CFPSent = true;

        }
    }

    public boolean done() {
        return myAgent.CFPSent;
    }
}

/**
*   Receive all proposals/refusals from bidders and find the highest bidder.
*/
class ReceiveBids extends Behaviour {
 
    private Auctioneer myAgent;
    private ArrayList<Integer> value ;
    private int repliesCnt = 0; // The counter of replies from seller agents

    public ReceiveBids(Auctioneer agent) {
        super(agent);
        myAgent = agent;
    }
    
    public void action() {

        if (myAgent.CFPSent) {

            // Receive all proposals/refusals from seller agents
            ACLMessage msg = myAgent.receive(myAgent.mt);
            if (msg != null) {
                // Bid received
                if (msg.getPerformative() == ACLMessage.PROPOSE) {

                    // This is an offer 
                    int price = Integer.parseInt(msg.getContent());
                    value = myAgent.priceList.get(myAgent.getFirstItemName());
                    if (price >= myAgent.getItemInitialPrice(myAgent.getFirstItemName())){
                        //add the bid in the priceList
                        value.add(price);
                        myAgent.priceList.put(myAgent.getFirstItemName(), value);
                    }
                        
                    if (myAgent.bestBidder == null || price > myAgent.bestPrice) {
                        // This is the best offer until now
                        myAgent.bestPrice = price;
                        myAgent.bestBidder = msg.getSender();
                    }

                    // Inform the bidder that the bid has been received
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("Your bid has been received!");
                    myAgent.send(reply);           
                }

                if (msg.getPerformative() == ACLMessage.REFUSE){
                    System.out.println(msg.getSender().getLocalName() + " is not joining this auction.");
                }

                repliesCnt++;
            }
            else {
                block();
            }
        
            if (repliesCnt >= myAgent.bidders.length) {
                // We have received all bids
                myAgent.bidsReceived = true;
            }
        }
    }
    
    public boolean done() {
        return myAgent.bidsReceived;
    }
}

/**
 * It announces the winner and updates the catalogue.
 * Send the request order to the bidder that provided the best offer.
 * @condition: if there is any winner
 */
class AnnounceWinner2 extends Behaviour {
    private ACLMessage msgCFP;
     
    private Auctioneer myAgent;

    private String itemName;

    private boolean isDone = false;
    private int p = 0;
    private int max1 = 0;
    private int max2 = 0;
    
    public AnnounceWinner2(Auctioneer agent, String itemName) {
        this.itemName = itemName;
        myAgent = agent;
    }
    
    public void action() {
        
        if (myAgent.bidsReceived) {

            if (myAgent.bestPrice >= myAgent.getItemInitialPrice(this.itemName)){
                // Send the purchase order to the seller that provided the best offer
                ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                order.addReceiver(myAgent.bestBidder);
                ArrayList<Integer> value = myAgent.priceList.get(this.itemName);
                int size = value.size();
                for(int i = 0; i< size; i++){//find the second maximum bid
                    if (value.get(i) > max1){
                        max2 = max1;
                        max1 = value.get(i);
                    }else if(value.get(i) > max2 && value.get(i)!=max1){
                        max2 = value.get(i);
                    }
                }
                if(max2 == 0){
                    max2 = max1;
                }
                p = max2;
                                    
                order.setContent(this.itemName + "," + p);
                order.setConversationId("blind-bid");
                order.setReplyWith("order"+System.currentTimeMillis());

                System.out.println("Announcing Winner for " + this.itemName + " !");

                Integer price = (Integer) myAgent.removeItemFromCatalogue(this.itemName);
                if (price != null) {
                    System.out.println(itemName+" sold to agent "+ myAgent.bestBidder.getName() + " !");                
                }
                else {
                    // The requested item has been sold to another buyer somehow
                    order.setPerformative(ACLMessage.FAILURE);
                    order.setContent("not-available");
                }
                myAgent.send(order);

                // Re-Initialize all conditions
                myAgent.biddersFound = false;
                myAgent.CFPSent = false;
                myAgent.bidsReceived = false;                
                myAgent.bestPrice = 0;
                myAgent.bestBidder = null;
            }
            else {
                System.out.println("There is no winner.. Bids were insufficient!");
            }
            isDone = true;
        }
    }

    public boolean done() {
        return isDone;
    }
}