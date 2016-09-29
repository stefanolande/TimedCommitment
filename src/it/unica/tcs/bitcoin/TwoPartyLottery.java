package it.unica.tcs.bitcoin;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.omg.CORBA.StringHolder;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Created by stefano on 28/09/16.
 */
public class TwoPartyLottery {

    WalletAppKit kit;
    NetworkParameters params;
    ECKey aliceKey, bobKey;
    String aliceSecretHash, bobSecretHash;

    Transaction computeTx;
    Coin bet;

    public static final Coin FEE = Coin.valueOf(50l);


    public TwoPartyLottery(WalletAppKit kit, NetworkParameters params, ECKey aliceKey, ECKey bobKey, String aliceSecretHash, String bobSecretHash) {
        this.kit = kit;
        this.params = params;
        this.aliceKey = aliceKey;
        this.bobKey = bobKey;
        this.aliceSecretHash = aliceSecretHash;
        this.bobSecretHash = bobSecretHash;
    }

    public void computePhase(TransactionOutput aliceIn, TransactionOutput bobIn){

        Coin bet = aliceIn.getValue().add(bobIn.getValue());

        computeTx.addSignedInput(aliceIn, aliceKey);
        computeTx.addSignedInput(bobIn, bobKey);

        this.computeTx = new Transaction(params);
        Script computeOutScript = getComputeOutScript();
        computeTx.addOutput(bet, computeOutScript);

        TransactionBroadcast txBroadcast = kit.peerGroup().broadcastTransaction(computeTx);

        mineBlock();

        try {
            txBroadcast.future().get();
            System.out.println("Compute transaction mined");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void aliceClaims(String aliceSecret, String bobSecret){

        Transaction claimTx = new Transaction(params);
        claimTx.addOutput(bet.subtract(FEE), aliceKey);

        TransactionInput input = claimTx.addInput(computeTx.getOutput(0));

        Sha256Hash claimTxHash = claimTx.hashForSignature(0, getComputeOutScript().getProgram(), Transaction.SigHash.ALL.byteValue());
        ECKey.ECDSASignature claimSignature = aliceKey.sign(claimTxHash);

        input.setScriptSig(getClaimMoneyInScript(aliceSecret, bobSecret, claimSignature));

        TransactionBroadcast txBroadcast = kit.peerGroup().broadcastTransaction(claimTx);
        mineBlock();

        try {
            txBroadcast.future().get();
            System.out.println("Claim_Alice transaction mined");
            System.out.println("Balance: " + kit.wallet().getBalance().toFriendlyString());

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void bobClaims(String aliceSecret, String bobSecret){

        Transaction claimTx = new Transaction(params);
        claimTx.addOutput(bet.subtract(FEE), bobKey);

        TransactionInput input = claimTx.addInput(computeTx.getOutput(0));

        Sha256Hash claimTxHash = claimTx.hashForSignature(0, getComputeOutScript().getProgram(), Transaction.SigHash.ALL.byteValue());
        ECKey.ECDSASignature claimSignature = bobKey.sign(claimTxHash);

        input.setScriptSig(getClaimMoneyInScript(aliceSecret, bobSecret, claimSignature));

        TransactionBroadcast txBroadcast = kit.peerGroup().broadcastTransaction(claimTx);
        mineBlock();

        try {
            txBroadcast.future().get();
            System.out.println("Claim_Alice transaction mined");
            System.out.println("Balance: " + kit.wallet().getBalance().toFriendlyString());

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private Script getComputeOutScript() {

        ScriptBuilder builder = new ScriptBuilder();

        //controllo che i segreti corrispondano agli hash committati in precendenza
        builder.op(ScriptOpCodes.OP_DUP);
        builder.op(ScriptOpCodes.OP_TOALTSTACK);
        builder.op(ScriptOpCodes.OP_HASH256);
        builder.data(bobSecretHash.getBytes());

        builder.op(ScriptOpCodes.OP_DUP);
        builder.op(ScriptOpCodes.OP_TOALTSTACK);
        builder.op(ScriptOpCodes.OP_HASH256);
        builder.data(aliceSecretHash.getBytes());

        //recupero SECRET_A dall'altstack e ne calcolo la lunghezza
        builder.op(ScriptOpCodes.OP_FROMALTSTACK);
        builder.op(ScriptOpCodes.OP_SIZE);
        builder.op(ScriptOpCodes.OP_NIP);

        //recupero SECRET_B dall'altstack e ne calcolo la lunghezza
        builder.op(ScriptOpCodes.OP_FROMALTSTACK);
        builder.op(ScriptOpCodes.OP_SIZE);
        builder.op(ScriptOpCodes.OP_NIP);

        //calcolo il risultato della lotteria

        //vedo se la lunghezza dei due segreti è uguale
        builder.op(ScriptOpCodes.OP_EQUAL);
        builder.op(ScriptOpCodes.OP_IF);

        //se è uguale vince alice
        builder.data(aliceKey.getPubKey());
        builder.op(ScriptOpCodes.OP_CHECKSIG);

        builder.op(ScriptOpCodes.OP_ELSE);

        //se è diversa vince bob
        builder.data(bobKey.getPubKey());
        builder.op(ScriptOpCodes.OP_CHECKSIG);

        builder.op(ScriptOpCodes.OP_ENDIF);

        return builder.build();
    }

    private Script getClaimMoneyInScript(String secretA, String secretB, ECKey.ECDSASignature signature){
        ScriptBuilder builder = new ScriptBuilder();

        TransactionSignature txSign = new TransactionSignature(signature, Transaction.SigHash.ALL, true);

        builder.data(txSign.encodeToBitcoin());
        builder.data(secretA.getBytes());
        builder.data(secretB.getBytes());

        return builder.build();
    }

    private static void mineBlock() {

        try {
            Runtime.getRuntime().exec("bitcoin-cli -regtest generate 1");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
