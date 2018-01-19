import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
// Behavior: CopyCat
// Recommended: evolution of trust
// http://ncase.me/trust//
// With consistent-behavior nodes it is reasonable to assume copycat wins the game.
// In here once a node refuses to comply it must be malicious.
// So a simple grudger will do.
public class CompliantNode implements Node {

    private double p_graph;
    private double p_malicious;
    private double p_txDistribution;
    private int numRounds;

    private boolean[] followees;
    private boolean[] blacklist;

    private Set<Transaction> pendingTransactions;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.p_graph = p_graph;
        this.p_malicious = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRounds = numRounds;
    }

    /** {@code followees[i]} is true if this node follows node {@code i} */
    public void setFollowees(boolean[] followees) {
        this.followees = followees;
        this.blacklist = new boolean[followees.length];
    }

    /** initialize proposal list of transactions */
    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        this.pendingTransactions = pendingTransactions;
    }

    /**
     *  @return  proposals to send to my followers.
     *  REMEMBER: After final round,
     *  behavior of { @code  getProposals} changes and it should return the
     *  transactions upon which consensus has been reached.
     */
    public Set<Transaction> sendToFollowers() {
        Set<Transaction> toSend = new HashSet<>(pendingTransactions);
        pendingTransactions.clear();
        return toSend;
    }

    /** receive candidates from other nodes. */
    public void receiveFromFollowees(Set<Candidate> candidates) {
        // we do not accept transactions from blacklisted nodes
        for (Candidate c : candidates) {
            if (!blacklist[c.sender]) {
                pendingTransactions.add(c.tx);
            }
        }
        Set<Integer> maliciousSet = getMaliciousSet(candidates);
        for (int i = 0; i < blacklist.length; i++) {
            // if the followee did comply, it is removed from blacklist
            if (blacklist[i] && !maliciousSet.contains(i)) {
                blacklist[i] = false;
            }
        }
        for (int i = 0; i < followees.length; i++) {
            // if the followee did not comply, it becomes blacklisted
            if (followees[i] && maliciousSet.contains(i)) {
                blacklist[i] = true;
            }
        }
    }

    private Set<Integer> getMaliciousSet(Set<Candidate> candidates) {
        // set of malicious nodes in this round
        Set<Integer> maliciousSet = new HashSet<>();
        // sender -> set of its sent transactions
        Map<Integer, Set<Integer>> sending = new HashMap<>();
        for (Candidate candidate : candidates) {
            int sender = candidate.sender;
            int id = candidate.tx.id;
            if (!sending.containsKey(sender)) {
                sending.put(sender, new HashSet<Integer>());
            } else {
                sending.get(sender).add(id);
            }
        }
        Set<Integer> followeesSet = new HashSet<>();
        for (int i = 0; i < followees.length; i++) {
            if (!followees[i]) continue;
            followeesSet.add(i);
        }
        // malicious suspect if functionally dead
        for (int i : followeesSet) {
            if (!sending.containsKey(i)) {
                // functionally dead
                maliciousSet.add(i);
            }
        }
        return maliciousSet;
    }
}
