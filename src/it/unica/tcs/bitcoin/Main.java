package it.unica.tcs.bitcoin;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;


/**
 * Created by stefano on 29/08/16.
 * <p>
 * Alice keys
 * PUB KEY: 	mzV4qo82N5zUM8aXUKtiw8aCNntm8J63QL
 * PRIV KEY: 	cN5CZVAUEMMCuiRFFsxvMsLoR3LAcKVmJGHkZxNik9qksk8ewa58
 * <p>
 * Bob keys
 * PUB KEY: mt42ndxahKY5jMdewFLZgxkQWezGU8ydSB
 * PRIV KEY: cReM8pbW7HKT4sgNFyDMSU88tKh6cSghdDUuRXgxY2kqWxPW2C1s
 */
public class Main {

    private static final String ALICE_PUB_KEY = "mzV4qo82N5zUM8aXUKtiw8aCNntm8J63QL";
    private static final String ALICE_PRIV_KEY = "cN5CZVAUEMMCuiRFFsxvMsLoR3LAcKVmJGHkZxNik9qksk8ewa58";
    private static final String BOB_PUB_KEY = "mt42ndxahKY5jMdewFLZgxkQWezGU8ydSB";
    private static final String BOB_PRIV_KEY = "cReM8pbW7HKT4sgNFyDMSU88tKh6cSghdDUuRXgxY2kqWxPW2C1s";

    private static final String SECRET = "1";




    public static void main(String[] args) {

        TimedCommitter cm = new TimedCommitter();

        NetworkParameters params = RegTestParams.get();
        String filePrefix = "forwarding-service-regtest";

        DumpedPrivateKey dumpAliceKey = DumpedPrivateKey.fromBase58(params, ALICE_PRIV_KEY);
        DumpedPrivateKey dumpBobKey = DumpedPrivateKey.fromBase58(params, BOB_PRIV_KEY);

        ECKey aliceKey = dumpAliceKey.getKey();
        ECKey bobKey = dumpBobKey.getKey();


        // Start up a basic app using a class that automates some boilerplate. Ensure we always have at least one key.
        WalletAppKit kit = new WalletAppKit(params, new File("."), filePrefix) {
            @Override
            protected void onSetupCompleted() {
                // This is called in a background thread after startAndWait is called, as setting up various objects
                // can do disk and network IO that may cause UI jank/stuttering in wallet apps if it were to be done
                // on the main thread.
                wallet().importKey(aliceKey);
                System.out.println(wallet().getKeyChainGroupSize());
            }
        };

        kit.connectToLocalHost();


        // Download the block chain and wait until it's done.
        kit.startAsync();
        kit.awaitRunning();


        System.out.println("connesso!");

        listenForNewTransactions(kit);

        System.out.println("Bilancio: " + kit.wallet().getBalance().toFriendlyString());
        //standardSend(kit, params);

        //Create the commit transaction
        Transaction commitTx = new Transaction(params);
        Coin deposit = Coin.valueOf(1, 5);
        Script commitOutScript = cm.getCommitOutScript(SECRET, aliceKey, bobKey);
        commitTx.addOutput(deposit, commitOutScript);

        SendRequest req = SendRequest.forTx(commitTx);

        try {
            kit.wallet().completeTx(req);

            TransactionBroadcast txBroadcast = kit.peerGroup().broadcastTransaction(req.tx);

            txBroadcast.future().get();

            final Sha256Hash commitTxhash = req.tx.getHash();
            System.out.println("Alice sent commit transaction with hash " + req.tx.getHashAsString());
            mineBlock();
            while (req.tx.isPending()) {
            }
            System.out.println("Transaction mined");

            Transaction openTx = new Transaction(params);
            openTx.addOutput(deposit, aliceKey);

            openTx.addInput(commitTx.getOutput(0));

            Sha256Hash openSigHash = openTx.hashForSignature(0, commitOutScript.getProgram(), Transaction.SigHash.ANYONECANPAY.byteValue());
            ECKey.ECDSASignature openSignature = aliceKey.sign(openSigHash);

            openTx.addInput(commitTxhash, 0, cm.getOpenInScript(SECRET, openSignature));

            kit.peerGroup().broadcastTransaction(openTx);

            mineBlock();
            while (req.tx.isPending()) {
            }
            System.out.println("Transaction mined");

            while (true);

        } catch (InsufficientMoneyException e) {
            e.printStackTrace();
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        } catch (ExecutionException e2) {
            e2.printStackTrace();
        }


    }

    private static void standardSend(WalletAppKit kit, NetworkParameters params) {
        Coin value = Coin.valueOf(0, 5);//kit.wallet().getBalance();
        System.out.println("Forwarding " + value.toFriendlyString() + " BTC");
        // Now send the coins back! Send with a small fee attached to ensure rapid confirmation.
        final Coin amountToSend = value.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        final Wallet.SendResult sendResult;
        try {
            sendResult = kit.wallet().sendCoins(kit.peerGroup(), Address.fromBase58(params, BOB_PUB_KEY), amountToSend);
            System.out.println("Sending ...");
            // Register a callback that is invoked when the transaction has propagated across the network.
            // This shows a second style of registering ListenableFuture callbacks, it works when you don't
            // need access to the object the future returns

            sendResult.broadcastComplete.addListener(() -> {// The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                        System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getHashAsString());
                    },
                    MoreExecutors.directExecutor());
        } catch (InsufficientMoneyException e) {
            e.printStackTrace();
        }
    }

    private static void mineBlock() {

        try {
            Runtime.getRuntime().exec("bitcoin-cli -regtest generate 1");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void listenForNewTransactions(WalletAppKit kit){
        kit.chain().addNewBestBlockListener(new NewBestBlockListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock storedBlock) throws VerificationException {
                System.out.println("New block received");
                ListenableFuture<Block> futureBlock = kit.peerGroup().getDownloadPeer().getBlock(storedBlock.getHeader().getHash());

                try {
                    Block b = futureBlock.get();
                    for(Transaction t : b.getTransactions()){
                        System.out.println("Transaction " + t);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
