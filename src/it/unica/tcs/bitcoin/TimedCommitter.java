package it.unica.tcs.bitcoin;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.wallet.SendRequest;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;

/**
 * Created by stefano on 31/08/16.
 */
public class TimedCommitter {

    WalletAppKit kit;
    NetworkParameters params;
    ECKey aliceKey, bobKey;
    String secret;

    Transaction commitTx;
    Coin deposit = Coin.valueOf(2, 0);


    public TimedCommitter(WalletAppKit kit, NetworkParameters params, ECKey aliceKey, ECKey bobKey, String secret) {
        this.kit = kit;
        this.params = params;
        this.aliceKey = aliceKey;
        this.bobKey = bobKey;
        this.secret = secret;
    }

    public static String sha256(String input) {
        MessageDigest mDigest = null;
        try {
            mDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] result = mDigest.digest(input.getBytes());
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < result.length; i++) {
            sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
        }

        return sb.toString();
    }

    private static void mineBlock() {

        try {
            Runtime.getRuntime().exec("bitcoin-cli -regtest generate 1");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void commitPhase() {
        Transaction commitTx = new Transaction(params);
        Script commitOutScript = getCommitOutScript(secret, aliceKey, bobKey);
        commitTx.addOutput(deposit, commitOutScript);

        SendRequest req = SendRequest.forTx(commitTx);

        try {
            kit.wallet().completeTx(req);

            TransactionBroadcast txBroadcast = kit.peerGroup().broadcastTransaction(req.tx);

            txBroadcast.future().get();

            System.out.println("Alice sent commit transaction with hash " + req.tx.getHashAsString());
            mineBlock();
            while (req.tx.isPending()) {
            }
            System.out.println("Commit transaction mined");
            System.out.println("Bilancio: " + kit.wallet().getBalance().toFriendlyString());

            this.commitTx = commitTx;

            //create the timelocked paydeposit
            //TODO


        } catch (InsufficientMoneyException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void openPhase() {
        Transaction openTx = new Transaction(params);
        openTx.addOutput(deposit, aliceKey);

        TransactionInput input = openTx.addInput(commitTx.getOutput(0));

        Sha256Hash openSigHash = openTx.hashForSignature(0, getCommitOutScript(secret, aliceKey, bobKey).getProgram(), Transaction.SigHash.ALL.byteValue());
        ECKey.ECDSASignature openSignature = aliceKey.sign(openSigHash);

        input.setScriptSig(getOpenInScript(secret, openSignature));

        TransactionBroadcast txBroadcast = kit.peerGroup().broadcastTransaction(openTx);


        mineBlock();
        try {
            txBroadcast.future().get();
            System.out.println("Open transaction mined");
            System.out.println("Balance: " + kit.wallet().getBalance().toFriendlyString());

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private Script getOpenInScript(String secret, ECKey.ECDSASignature signature) {
        ScriptBuilder builder = new ScriptBuilder();

        TransactionSignature txSign = new TransactionSignature(signature, Transaction.SigHash.ALL, true);

        builder.data(txSign.encodeToBitcoin());

        builder.data(new byte[]{}); //signature of bob (empty)

        builder.data(secret.getBytes());

        return builder.build();

    }

    private Script getCommitOutScript(String secret, ECKey aliceKey, ECKey bobKey) {


        ScriptBuilder builder = new ScriptBuilder();

        //sposto le due firme nell'altstack
        builder.op(ScriptOpCodes.OP_TOALTSTACK);
        builder.op(ScriptOpCodes.OP_TOALTSTACK);

        //controllo che il segreto sia giusto
        builder.op(ScriptOpCodes.OP_SHA256);
        builder.data(sha256(secret).getBytes());
        builder.op(ScriptOpCodes.OP_EQUAL);

        //copio la firma di alice dall'altstack
        builder.op(ScriptOpCodes.OP_FROMALTSTACK);
        builder.op(ScriptOpCodes.OP_DUP);
        builder.op(ScriptOpCodes.OP_TOALTSTACK);

        //controllo la firma di alice
        builder.data(aliceKey.getPubKey());
        builder.op(ScriptOpCodes.OP_CHECKSIG);

        //sia il segreto che la firma devono essere verificati
        builder.op(ScriptOpCodes.OP_AND);

        //sposto dall'altstack la firma di alice e la controllo
        builder.op(ScriptOpCodes.OP_FROMALTSTACK);
        builder.data(aliceKey.getPubKey());
        builder.op(ScriptOpCodes.OP_CHECKSIG);

        //sposto dall'altstack la firma di bob e la controllo
        builder.op(ScriptOpCodes.OP_FROMALTSTACK);
        builder.data(bobKey.getPubKey());
        builder.op(ScriptOpCodes.OP_CHECKSIG);

        //entrambe le firme devono essere verificate
        builder.op(ScriptOpCodes.OP_AND);

        //la prima condizione deve essere vera o la seconda
        builder.op(ScriptOpCodes.OP_OR);

        return builder.build();
    }


}
