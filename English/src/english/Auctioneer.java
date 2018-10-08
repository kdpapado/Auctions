package english;

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
 * This class creates a JADE agent who performs an auctioneer of an english auction.
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
    
    //Previous' "time circle" best price and bidder
    public int previousPrice=0;
    public AID previousBidder=null;
    
    //the count of bidders
    public int biddersCount=0;

    public FindBidder p = null;
    public SendCFP q = null;
    public ReceiveBids r = null;
    public AnnounceWinner s = null;
    
    public void setAuctioneer(HashMap priceList, int previousPrice, AID previousBidder) {

    this.priceList=priceList;
    this.previousPrice=previousPrice;
    this.previousBidder=previousBidder;
    
    auctionBegan = false;
    mt=null; 
    bidders=null;
    bestPrice=0;
    biddersFound = false;
    CFPSent = false;
    bidsReceived = false;
    
    p = null;
    q = null;
    r = null;
    s = null;
    
    setup();
    }
    /**
    *    Set-up the auctioneer and the auction system.
    */
    int flag=0;
    @Override
    protected void setup() {
        //first cycle
        if (flag==0) {
        flag++;
        priceList= new HashMap<String,ArrayList<Integer>>();
                            
        // show a starting message
        
	System.out.println("The auctions starts!  Auctioneer "+getAID().getName()+" is ready.");

	// Create the catalogue
	catalogue = new Hashtable();

	// Create and show the GUI window
	myGUI = new AuctioneerGUI(this);
	myGUI.showGui();
       
        // Add a Behaviour that schedules a request to bidders
        addBehaviour(new ActionBid(this));
        }
        //following cycles
        else {
            addBehaviour(new ActionBid(this));
        }
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
                    priceList= new HashMap<String,ArrayList<Integer>>();
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
        addBehaviour(new ActionBid(this));
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
 *  Add a Behaviour that schedules a request to bidders.
*/
class ActionBid extends Behaviour {

    private Auctioneer myAgent;

    private String currentItemName;

    public ActionBid(Auctioneer agent) {
        super(agent);
        myAgent = agent;
    }

    @Override
    public void action(){
        
        // If there is any item to sell
        if (!myAgent.isCatalogueEmpty()) {

                if (myAgent.p != null) myAgent.removeBehaviour(myAgent.p);
                if (myAgent.q != null) myAgent.removeBehaviour(myAgent.q);
                if (myAgent.r != null) myAgent.removeBehaviour(myAgent.r);
                if (myAgent.s != null) myAgent.removeBehaviour(myAgent.s);
                
                if (myAgent.previousPrice==0)
                    currentItemName = myAgent.getFirstItemName();
                else
                    currentItemName = myAgent.getFirstItemName();  
                System.out.println("Starting auction for item " + currentItemName+ ".");
                System.out.println("Waiting for bidders...");

                // Find Bidder
                myAgent.p = new FindBidder(myAgent);
                myAgent.addBehaviour(myAgent.p);

                // Send CFP to all bidders
                myAgent.q = new SendCFP(myAgent, currentItemName, myAgent.getItemInitialPrice(currentItemName), myAgent.previousPrice);
                myAgent.addBehaviour(myAgent.q);

                // Receive all proposals/refusals from bidders and find the highest bidder
                myAgent.r = new ReceiveBids(myAgent);
                myAgent.addBehaviour(myAgent.r);

                myAgent.s = new AnnounceWinner(myAgent, currentItemName);
                myAgent.addBehaviour(myAgent.s);

        }        
        else {
            System.out.println("Add an item before we can commence auctions.");          
        }
    }

    @Override
    public boolean done() {
        return true;
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
            sd.setType("english-auction");
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
                myAgent.biddersCount = myAgent.bidders.length;
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
    private int previousPrice;

    public SendCFP(Auctioneer agent, String itemName, int itemInitialPrice, int previousPrice) {
        super(agent);
        myAgent = agent;
        this.itemName = itemName;
        this.itemInitialPrice = itemInitialPrice;
        this.previousPrice = previousPrice;
    }
    
    public void action() {
        
        if (!myAgent.CFPSent && myAgent.biddersFound) {

            // Send the cfp to all bidders
            System.out.println("Sending CFP to all bidders..");
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            for (int i = 0; i < myAgent.bidders.length; ++i) {
                cfp.addReceiver(myAgent.bidders[i]);
            } 
            cfp.setContent(this.itemName + "," + this.itemInitialPrice + "," + this.previousPrice);
            cfp.setConversationId("english-bid");
            cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
            myAgent.send(cfp);

            // Prepare message template
            myAgent.mt = MessageTemplate.and(MessageTemplate.MatchConversationId("english-bid"),
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
    private boolean receiveFlag = false;
    private int count=0;

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
                        //setBestPrice(myAgent.bestPrice);
                        myAgent.bestBidder = msg.getSender();
                        receiveFlag=true;
                    }

                    // Inform the bidder that the bid has been received
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("Your bid has been received!");
                    myAgent.send(reply);           
                }

                if (msg.getPerformative() == ACLMessage.REFUSE){
                    System.out.println(msg.getSender().getLocalName() + " is not joining this auction.");
                    myAgent.biddersCount--;
                    receiveFlag=true;
                }
                
                if (msg.getPerformative() == ACLMessage.CANCEL){
                    System.out.println(msg.getSender().getLocalName() + " has not enough budget to join this auction.");
                    myAgent.biddersCount--;
                    count++;
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
    
    public boolean getReceiveFlag() {
        return receiveFlag;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setReceiveFlag(boolean f) {
        receiveFlag = f;
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
class AnnounceWinner extends Behaviour {
    private ACLMessage msgCFP;
     
    private Auctioneer myAgent;

    private String itemName;

    private boolean isDone = false;
    
    public AnnounceWinner(Auctioneer agent, String itemName) {
        this.itemName = itemName;
        myAgent = agent;
    }
    
    public void action() {
        
        if (myAgent.bidsReceived) {
            int length = myAgent.bidders.length-1;
            boolean equal = false;
            if (myAgent.previousBidder != null && myAgent.bestBidder != null)
                equal = myAgent.previousBidder.equals(myAgent.bestBidder);
            
            //if there is no new price for the item 
            //or there is no new bidder (previousBidder is the same with bestBidder)
            //or there is only one who can buy it
            if ( (myAgent.bestPrice==0 && myAgent.previousPrice!=0) 
                    || (myAgent.biddersCount==1 && equal) 
                    || (myAgent.r.getCount()==length && myAgent.bestPrice!=0)){

                ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                order.addReceiver(myAgent.bestBidder);
                ArrayList<Integer> value = myAgent.priceList.get(this.itemName);
                 
                if (myAgent.previousPrice==0)
                    order.setContent(this.itemName + "," + myAgent.bestPrice);
                else
                    order.setContent(this.itemName + "," + myAgent.previousPrice);
                
                order.setConversationId("english-bid");
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
                myAgent.previousPrice = 0;
                myAgent.priceList = null;
                myAgent.r.setReceiveFlag(false);
                myAgent.previousBidder = null;
                myAgent.bestBidder = null;
                System.out.println("Add an item before we can commence auctions.");
            }
            
            //if all bidders have not enough budget for the auction
            else if (myAgent.r.getCount()==myAgent.bidders.length) {

                ArrayList<Integer> value = myAgent.priceList.get(this.itemName);
                Integer price = (Integer) myAgent.removeItemFromCatalogue(this.itemName);
                if (price != null) {
                    System.out.println(itemName+" cannot be sold to any agent !");                
                }

                // Re-Initialize all conditions for next item
                myAgent.biddersFound = false;
                myAgent.CFPSent = false;
                myAgent.bidsReceived = false;                
                myAgent.bestPrice = 0;
                myAgent.bestBidder = null;
                myAgent.previousPrice = 0;
                myAgent.priceList = null;
                myAgent.r.setReceiveFlag(false);
                myAgent.bestBidder = null;
                myAgent.previousBidder = null;
                System.out.println("Add an item before we can commence auctions.");
            }
            else {
                myAgent.r.setReceiveFlag(false);
                System.out.println("There is no winner yet.. Next cycle..");
                myAgent.previousPrice = myAgent.bestPrice;
                myAgent.previousBidder = myAgent.bestBidder;
                myAgent.setAuctioneer(myAgent.priceList,myAgent.previousPrice,myAgent.previousBidder);

            }
            isDone = true;
        }
    }

    public boolean done() {
        return isDone;
    }
}