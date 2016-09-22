package it.unica.tcs.bitcoin;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by stefano on 31/08/16.
 */
public class TimedCommitter {

    public Script getOpenInScript(String secret, ECKey.ECDSASignature signature){
        ScriptBuilder builder = new ScriptBuilder();

        TransactionSignature txSign = (TransactionSignature) signature;

        builder.data(txSign.encodeToBitcoin());

        builder.data(new byte[]{}); //signature of bob (empty)

        builder.data(secret.getBytes());

        return builder.build();

    }

    public Script getCommitOutScript(String secret, ECKey aliceKey, ECKey bobKey){


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

    static String sha256(String input)  {
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

}
