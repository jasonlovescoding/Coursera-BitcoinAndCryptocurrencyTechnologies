// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;

    private TransactionPool txPool;
    private Map<ByteArrayWrapper, BlockNode> hashToNode;
    private BlockNode maxHeightNode;
    /**
     * create an empty block chain with just a genesis block.
     * Assume {@code genesisBlock} is a valid block
     */
    public BlockChain(Block genesisBlock) {
        txPool = new TransactionPool();
        UTXOPool utxoPool = new UTXOPool();
        addCoinbaseToUTXOPool(genesisBlock, utxoPool);
        BlockNode genesisNode = new BlockNode(genesisBlock, null, utxoPool);
        hashToNode = new HashMap<>();
        hashToNode.put(wrap(genesisBlock.getHash()), genesisNode);
        maxHeightNode = genesisNode;
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightNode.block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightNode.utxoPool;
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return txPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <= CUT_OFF_AGE + 1}.
     * As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block at height 2.
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        byte[] prevBlockHash = block.getPrevBlockHash();
        if (prevBlockHash == null) {
            // a new genesis block will not be mined
            return false;
        }
        BlockNode parentBlockNode = hashToNode.get(wrap(prevBlockHash));
        if (parentBlockNode == null) {
            // the parent block must be valid
            return false;
        }
        TxHandler txHandler = new TxHandler(parentBlockNode.getUTXOPoolCopy());
        ArrayList<Transaction> txList = block.getTransactions();
        Transaction[] txs = txList.toArray(new Transaction[txList.size()]);
        Transaction[] validTxs = txHandler.handleTxs(txs);
        // all transactions must be valid
        if (txs.length != validTxs.length) {
            return false;
        }
        int proposedHeight = parentBlockNode.height + 1;
        if (proposedHeight <= maxHeightNode.height - CUT_OFF_AGE) {
            // outdated height
            return false;
        }
        // if all held true, add the new coinbase outputs into the pool
        UTXOPool utxoPool = txHandler.getUTXOPool();
        addCoinbaseToUTXOPool(block, utxoPool);
        BlockNode node = new BlockNode(block, parentBlockNode, utxoPool);
        hashToNode.put(wrap(block.getHash()), node);
        // if a higher node is created, update the max-height node
        if (proposedHeight > maxHeightNode.height) {
            maxHeightNode = node;
        }
        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        txPool.addTransaction(tx);
    }

    // while being a chain, a BlockChain is effectively a tree with possible forks
    // it is comprised of nodes with a parent pointer and a height indicator
    // as well as the UTXO pool at the time of its initialization
    private class BlockNode {
        public Block block;
        public BlockNode parent;
        public int height;
        public UTXOPool utxoPool;

        public BlockNode(Block block, BlockNode parent, UTXOPool utxoPool) {
            this.block = block;
            this.parent = parent;
            this.height = (parent == null) ? 1 : parent.height + 1;
            this.utxoPool = new UTXOPool(utxoPool);
        }

        public UTXOPool getUTXOPoolCopy() {
            return new UTXOPool(utxoPool);
        }
    }

    // add coinbase outputs into utxo pool
    private void addCoinbaseToUTXOPool(Block block, UTXOPool utxoPool) {
        Transaction coinbase = block.getCoinbase();
        for (int i = 0; i < coinbase.numOutputs(); i++) {
            Transaction.Output out = coinbase.getOutput(i);
            UTXO utxo = new UTXO(coinbase.getHash(), i);
            utxoPool.addUTXO(utxo, out);
        }
    }

    // wrap the byte array into a comparable byte-array object
    private static ByteArrayWrapper wrap(byte[] arr) {
        return new ByteArrayWrapper(arr);
    }
}