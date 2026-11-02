package com.dropwave.tv;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

/** Device-local TLS key secured by AndroidKeyStore; never exported. */
final class TlsAndroid {
    static final String ALIAS="edo-lan-tls-2026";
    static SSLContext context()throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyPairGenerator gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
            Calendar from=Calendar.getInstance(),until=Calendar.getInstance();until.add(Calendar.YEAR,10);
            KeyGenParameterSpec spec=new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
                .setKeySize(3072).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=Edo Local Transfer,O=Edo,C=US"))
                .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                .setCertificateNotBefore(from.getTime()).setCertificateNotAfter(until.getTime())
                .setUserAuthenticationRequired(false).build();
            gen.initialize(spec);gen.generateKeyPair();
        }
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,null);
        SSLContext context=SSLContext.getInstance("TLS");context.init(km.getKeyManagers(),null,new SecureRandom());
        return context;
    }
    /** Export only the public X.509 certificate. Keep the private key inside AndroidKeyStore. */
    static byte[] certificateDer() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        Certificate c=ks.getCertificate(ALIAS);
        if(c==null)throw new IllegalStateException("Enable HTTPS first to create a certificate");
        return c.getEncoded();
    }
    static String certificateDetails() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        Certificate raw=ks.getCertificate(ALIAS);
        if(!(raw instanceof java.security.cert.X509Certificate))return "No TLS certificate yet";
        java.security.cert.X509Certificate cert=(java.security.cert.X509Certificate)raw;
        java.util.Collection<java.util.List<?>> sans=cert.getSubjectAlternativeNames();
        return "SHA-256 fingerprint:\n"+fingerprint()+"\n\nValid until: "+cert.getNotAfter()+
            "\nIP-address SAN: "+(sans==null||sans.isEmpty()?"Missing (some browsers will refuse this certificate)":"Present (check the current IP address)");
    }
    static String fingerprint()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        Certificate c=ks.getCertificate(ALIAS);if(c==null)return "Not initialized";
        byte[] b=MessageDigest.getInstance("SHA-256").digest(c.getEncoded());StringBuilder out=new StringBuilder();
        for(byte v:b)out.append(String.format(java.util.Locale.US,"%02X:",v));return out.substring(0,out.length()-1);
    }
}
