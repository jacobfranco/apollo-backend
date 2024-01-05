package com.apollo.backend.api;

import java.io.*;
import java.security.*;
import org.bouncycastle.util.io.pem.*;

public class ApolloWebHelpers {

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }    

    public static class SigningKeyPair {
        public String publicKey;
        public String privateKey;

        public SigningKeyPair(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
    }

     public static SigningKeyPair generateKeys() throws NoSuchProviderException, NoSuchAlgorithmException, IOException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "BC");
        KeyPair pair = generator.generateKeyPair();
        Key privKey = pair.getPrivate();
        Key pubKey = pair.getPublic();
        StringWriter privStringWriter = new StringWriter();
        try (PemWriter privWriter = new PemWriter(privStringWriter)) {
            privWriter.writeObject(new PemObject("PRIVATE KEY", privKey.getEncoded()));
        }
        StringWriter pubStringWriter = new StringWriter();
        try (PemWriter pubWriter = new PemWriter(pubStringWriter)) {
            pubWriter.writeObject(new PemObject("PUBLIC KEY", pubKey.getEncoded()));
        }
        return new SigningKeyPair(pubStringWriter.toString(), privStringWriter.toString());
    }
    
}
