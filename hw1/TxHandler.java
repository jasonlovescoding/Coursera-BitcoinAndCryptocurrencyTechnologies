import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

public class TxHandler {
    private UTXOPool pool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.pool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {   
        ArrayList<Transaction.Input> inputs = tx.getInputs();   
        HashSet<UTXO> utxos = new HashSet<>();

        double inValue = 0.0;
        for (int index = 0; index < inputs.size(); index++) {
            Transaction.Input input = inputs.get(index);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            // (1)
            if (!this.pool.contains(utxo)) {
                return false;
            }
            // (2)
            Transaction.Output output = this.pool.getTxOutput(utxo);
            if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(index), input.signature)) {
                return false;
            }
            // (3)
            if (utxos.contains(utxo)) {
                return false;
            }
            utxos.add(utxo);
            inValue += output.value;
        }    

        double outValue = 0.0;
        for (Transaction.Output output : tx.getOutputs()) {
            // (4)
            if (output.value < 0) {
                return false;
            }
            outValue += output.value;
        }
        // (5)
        if (inValue < outValue) {
            return false;
        }
        return true;    
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // use a simple heuristic: 
        // always pick the first valid transaction and update,
        // until no more transaction is valid
        boolean allInvalid = false;
        HashSet<Transaction> txs = new HashSet<>(Arrays.asList(possibleTxs));
        ArrayList<Transaction> validTxs = new ArrayList<>();
        while (!allInvalid) {
            allInvalid = true;
            Iterator<Transaction> iter = txs.iterator();
            while (iter.hasNext()) {
                Transaction tx = iter.next();
                // if a valid transaction is detected
                if (isValidTx(tx)) {
                    // remove all the outputs it claims
                    for (Transaction.Input input : tx.getInputs()) {
                        UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                        this.pool.removeUTXO(utxo);
                    }
                    // and add all the outputs it generates
                    ArrayList<Transaction.Output> outputs = tx.getOutputs();
                    for (int index = 0; index < outputs.size(); index++) {
                        UTXO utxo = new UTXO(tx.getHash(), index);
                        this.pool.addUTXO(utxo, outputs.get(index));
                    }
                    // add this transaction to the result and remove it from the set
                    validTxs.add(tx);
                    iter.remove();
                    allInvalid = false;
                }
            }
        }
        return validTxs.toArray(new Transaction[validTxs.size()]);
    }

}
