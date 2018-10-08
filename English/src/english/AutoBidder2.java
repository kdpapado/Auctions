package english;

import static english.ReceiveCFPAs.showRandomInteger;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 
 * @author Eirini Mitsopoulou
 * @author Kyriaki-Nektaria Pantelidou
 * @author Konstantina Papadopoulou
 */


/**
 * This class creates a JADE agent who performs a bidder of an english auction.
 * It will terminate after the budget runs out.
 */
public class AutoBidder2 extends Agent{

    //  The name of the item that is to be sold
    public String itemName;

    // Check if CFP received
    public boolean CFPReceived = false;

    // The budget left for this bidder
    public int budget;

    // Random number generator
    static Random rn = new Random();

    // Agent initializations
    protected void setup() {

        // Setup budget randomly between [1000,2000]
        budget = rn.nextInt(1000) + 1000;
	System.out.println("Bidder "+getAID().getName()+" is ready with budget " + budget + ".");

	// Register as bidder to the yellow pages
	DFAgentDescription dfd = new DFAgentDescription();
	dfd.setName(getAID());
	ServiceDescription sd = new ServiceDescription();
	sd.setType("english-auction");
	sd.setName("english-Auction");
	dfd.addServices(sd);
	try {
            DFService.register(this, dfd);
	}
	catch (FIPAException fe) {
            fe.printStackTrace();
	}

	// Add the behaviour for receiving CFP from Auctioneer
	addBehaviour(new ReceiveCFPAs2(this));

	// Add the behaviour for receiving item when win the auction
	addBehaviour(new ReceiveItemAsWinner2(this));

	// Add the behaviour for receiving INFORM
	addBehaviour(new ReceiveINFORM2());
    }

    // Agent clean-up operations
    protected void takeDown() {
        // Deregister from the yellow pages
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Show a dismissal message
        System.out.println("Bidder "+getAID().getName()+" terminating.");
    }
}

/**
 * This processes CFP , deciding whether to bid on a specific item or not.
 */
class ReceiveCFPAs2 extends CyclicBehaviour {

    private AutoBidder2 myAgent;

    public ReceiveCFPAs2(AutoBidder2 agent) {
        super(agent);
        myAgent = agent;
    }

    public void action() {
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
        ACLMessage msg = myAgent.receive(mt);

        // Check budget -> If it is 0, terminate
        if (myAgent.budget <= 0){
            System.out.println("No budget left!");
            myAgent.doDelete();
        }

        if (msg != null) {
            // CFP Message received. Process it.
            String ans = msg.getContent();
            String[] components = ans.split(",");
            String itemName = components[0];
            int itemInitPrice = Integer.parseInt(components[1]);
            int previousPrice = Integer.parseInt(components[2]);
            ACLMessage reply = msg.createReply();
            
                // It follows the strategy of the bidder
                int START = itemInitPrice;
                int END = myAgent.budget + myAgent.budget/2;
                int randInt = 0;
                Random random = new Random();

                // Check if the bidder has the money to buy the item
                if (myAgent.budget >= itemInitPrice && myAgent.budget >= previousPrice) {
                do
                {
                   // Take one random number between [itemInitialPrice,budget/2]
                   randInt = showRandomInteger(START, END, random);
                }while(randInt>myAgent.budget || randInt<=previousPrice);   
                //until the bid price is less than the budget and more than the previous price
                }

                // This is the bid price -> the initial price of the item + the random number that we took above
                int bidPrice = randInt;


                System.out.println("Auction commenced. Current item is: " + itemName+ ".");
                System.out.println("Current item initial price is: " + itemInitPrice + ".");

                //Join the auction 1 out of 2 times
                // Check if budget is adequate 
                int randomNum = ThreadLocalRandom.current().nextInt(1, 2 + 1);
                if(randomNum==1 && (myAgent.budget >= itemInitPrice && myAgent.budget >= previousPrice)) {
                        // Send the bid
                        myAgent.itemName = itemName;
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent(String.valueOf(bidPrice));
                        System.out.println(myAgent.getLocalName() + " sent bid with price " + bidPrice+ ".");
                }
                // Else, bidder can not join the auction
                else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("Not joining this one...");
                    System.out.println(myAgent.getLocalName() + " is not joining this auction.");
                }
                
                //does not have enough budget to participate
                if (itemInitPrice > myAgent.budget) {
                    reply.setPerformative(ACLMessage.CANCEL);
                    reply.setContent("Not joining this one...");
                    System.out.println(myAgent.getLocalName() + " has not enough budget to join this auction.");
                }

            myAgent.send(reply);
        }
        else {
            block();
        }
    }
    
}


/**
 * Get the item as the auction winner.
 */
class ReceiveItemAsWinner2 extends CyclicBehaviour {

    private AutoBidder2 myAgent;

    public ReceiveItemAsWinner2(AutoBidder2 agent) {
        super(agent);
        myAgent = agent;
    }

    public void action() {
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
        ACLMessage msg = myAgent.receive(mt);
        if (msg != null) {
            // ACCEPT_PROPOSAL Message has been received. Process it.
            String ans = msg.getContent();
            String[] parts = ans.split(",");
            String itemName = parts[0];
            int price = Integer.parseInt(parts[1]);
            ACLMessage reply = msg.createReply();

            reply.setPerformative(ACLMessage.INFORM);
            System.out.println("Congratulations! You have won the auction!");
            System.out.println(itemName+" is now yours! With the price " + price+ " .");

            myAgent.send(reply);

            // Subtract the money from budget
            myAgent.budget -= price;            
        }
        else {
            block();
        }
    }
}

/**
 * This receives the INFORM messages.
 */
class ReceiveINFORM2 extends CyclicBehaviour {
    public void action() {
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
        ACLMessage msg = myAgent.receive(mt);
        if (msg != null) {
            // INFORM Message has been received, thus show it.
            System.out.println(msg.getContent());
        }
        else {
            block();
        }
    }
}
