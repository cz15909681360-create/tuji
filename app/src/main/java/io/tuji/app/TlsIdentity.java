package io.tuji.app;
import android.security.keystore.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.security.*;
import java.security.cert.*;
import java.math.BigInteger;
import java.util.*;

/** Stable AndroidKeyStore identity; TLS handshake proves key possession. */
public final class TlsIdentity {
 public final SSLContext context;public final String fingerprint;
 public TlsIdentity()throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  String alias="tuji-rsa-identity-v2";
  if(!ks.containsAlias(alias)){
   KeyPairGenerator g=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
   g.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
    .setKeySize(2048).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS).setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512)
    .setCertificateSubject(new X500Principal("CN=Tuji LocalSend"))
    .setCertificateSerialNumber(new BigInteger(128,new SecureRandom())).setCertificateNotBefore(new Date(System.currentTimeMillis()-86400000L))
    .setCertificateNotAfter(new Date(4102444800000L)).build());g.generateKeyPair();
  }
  X509Certificate cert=(X509Certificate)ks.getCertificate(alias);fingerprint=fingerprint(cert);
  KeyStore.PrivateKeyEntry entry=(KeyStore.PrivateKeyEntry)ks.getEntry(alias,null);
  X509ExtendedKeyManager km=new X509ExtendedKeyManager(){
   public String[] getClientAliases(String t,java.security.Principal[] i){return new String[]{alias};}
   public String chooseClientAlias(String[] t,java.security.Principal[] i,java.net.Socket s){android.util.Log.d("TujiTLS","Client certificate requested: "+java.util.Arrays.toString(t));return alias;}
   public String chooseEngineClientAlias(String[] t,java.security.Principal[] i,SSLEngine e){return alias;}
   public String chooseEngineServerAlias(String t,java.security.Principal[] i,SSLEngine e){return "RSA".equals(t)?alias:null;}
   public String[] getServerAliases(String t,java.security.Principal[] i){return "RSA".equals(t)?new String[]{alias}:null;}
   public String chooseServerAlias(String t,java.security.Principal[] i,java.net.Socket s){return "RSA".equals(t)?alias:null;}
   public X509Certificate[] getCertificateChain(String a){return new X509Certificate[]{cert};}
   public PrivateKey getPrivateKey(String a){return entry.getPrivateKey();}
  };
  X509TrustManager trust=new X509TrustManager(){
   public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
   public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{verify(c);}
   public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{verify(c);}
   private void verify(X509Certificate[] chain)throws CertificateException{if(chain==null||chain.length==0)throw new CertificateException("Missing certificate");try{chain[0].checkValidity();chain[0].verify(chain[0].getPublicKey());}catch(Exception e){throw new CertificateException("Invalid self-signed identity",e);}}
  };
  context=SSLContext.getInstance("TLS");context.init(new KeyManager[]{km},new TrustManager[]{trust},new SecureRandom());
 }
 public static String fingerprint(X509Certificate c)throws Exception{return PhotoBundle.hex(PhotoBundle.digest().digest(c.getEncoded())).toUpperCase(Locale.ROOT);}
 public static String peer(SSLSocket s)throws Exception{return fingerprint((X509Certificate)s.getSession().getPeerCertificates()[0]);}
}
