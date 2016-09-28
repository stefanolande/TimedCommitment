package it.unica.tcs.bitcoin;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.omg.CORBA.StringHolder;

/**
 * Created by stefano on 28/09/16.
 */
public class TwoPartyLottery {

    WalletAppKit kit;
    NetworkParameters params;
    ECKey aliceKey, bobKey;
    String aliceSecretHash, bobSecretHash;

    Transaction commitTx;
    Coin deposit = Coin.valueOf(2, 0);

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
}
