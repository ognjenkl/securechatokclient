import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.NoSuchPaddingException;

import org.json.JSONException;
import org.json.JSONObject;

import secureLib.CryptoImpl;
import secureUtil.MessageType;


public class ChatClientThreadReader extends Thread{

	BufferedReader in;
	
	public void run(){
		try {
			//in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			in = new BufferedReader(new InputStreamReader(ChatClient.getInstance().getSocket().getInputStream()));
			
			String request = "";
			while ((request = in.readLine()) != null){
				//{"data":"og;","from":"s","to":"og","type":"updateUsers"}
				//{"data":"og;dr;","from":"s","to":"og","type":"updateUsers"}
				//{"data":"pozdrav og","from":"dr","to":"og","type":"chat"}
				//{"data":"Korisnik \"og\" se odjavio!","from":"og","to":"dr","type":"server"}
				//System.out.println("Reader gets request: " + request);
				
				String requestDecrypted = decryptMessage(request);
				System.out.println("Reader gets request (decrypted): " + requestDecrypted);
				
				JSONObject jsonObject = new JSONObject(requestDecrypted);
				String to = jsonObject.getString("to");
				String from = jsonObject.getString("from");
				String type = jsonObject.getString("type");
				String data = jsonObject.getString("data");
				
				if(type.equals(MessageType.CHAT)){ 
					//remote client wants to chat
					String messageChat = "";
					
					JSONObject jsonToVerify = new JSONObject(data);
					PublicKey pubKey = ChatClient.getInstance().getUsersAndPublicKeys().get(from);
					if ( CryptoImpl.verifyDigitalSignature(jsonToVerify.getString(MessageType.CIPHER), 
							jsonToVerify.getString(MessageType.DIGSIG), 
							pubKey, 
							ChatClient.getInstance().getOpModeAsymmetric(), 
							ChatClient.getInstance().getPrivateKeyPair(),
							ChatClient.getInstance().getRemoteClientsInCommunication().get(from).getOpModeSymmetric(),
							ChatClient.getInstance().getRemoteClientsInCommunication().get(from).getSymmetricKeyChat(),
							ChatClient.getInstance().getRemoteClientsInCommunication().get(from).getHashFunction())){
						
						System.out.println("Digitalni potpis chat ISPRAVAN");
						
						byte[] cipherDecoded = Base64.getDecoder().decode(jsonToVerify.getString(MessageType.CIPHER).getBytes(StandardCharsets.UTF_8));
						byte[] cipherDecrypted = CryptoImpl.symmetricEncryptDecrypt(ChatClient.getInstance().getRemoteClientsInCommunication().get(from).getOpModeSymmetric(), ChatClient.getInstance().getRemoteClientsInCommunication().get(from).getSymmetricKeyChat(), cipherDecoded, false);
						byte[] cipherDecryptedDecoded = Base64.getDecoder().decode(cipherDecrypted);
						messageChat = new String(cipherDecryptedDecoded, StandardCharsets.UTF_8);
						
						ChatClient.getInstance().getRemoteClientsInCommunication().get(from).writeToHistory(from, messageChat);
					}
					else
						System.out.println("Digitalni potpis chat NIJE ispravan");
					
				} else if (type.equals(MessageType.UPDATE)) {
					//update users chat list
					//ChatClient.getInstance().updateListUsersGui(ChatClient.getInstance().stringToList(data,";"));
					ChatClient.getInstance().updateListUsersGui(data);
				} else if (type.equals(MessageType.SERVER)){ 
					//message from server
					ChatClient.getInstance().getRemoteClientsInCommunication().get(from).writeToHistory(type, data);
				} else if (type.equals(MessageType.PUBLICKEY)){
					//potrebno da client koji je trazio javni kljuc saceka (wait) da kljuc stinge i nastavi nakon notify
					//mehanizam nalaze da se taj thread zakljuca u bloc synchronized na oba mjesta
					synchronized (ChatClient.getInstance().getRemoteClientsInCommunication().get(from)) {
						PublicKey publicKey = CryptoImpl.deserializeRsaPublicKey(data);
						ChatClient.getInstance().getRemoteClientsInCommunication().get(from).setRemotePublicKey(publicKey);
						ChatClient.getInstance().getRemoteClientsInCommunication().get(from).notify();
					}
				} else if (type.equals(MessageType.CHATKEY)) {
					System.out.println("chatkey request: " + data);
					
					//provjeriti da li je poruka ispravna verifikovati
					JSONObject jsonToVerify = new JSONObject(data);
					PublicKey pubKey = ChatClient.getInstance().getUsersAndPublicKeys().get(from);
					if ( CryptoImpl.verifyDigitalSignature(jsonToVerify.getString(MessageType.CIPHER), 
							jsonToVerify.getString(MessageType.ENVELOPE), 
							jsonToVerify.getString(MessageType.DIGSIG), 
							pubKey, 
							ChatClient.getInstance().getOpModeAsymmetric(), 
							ChatClient.getInstance().getPrivateKeyPair())){
						System.out.println("Digitalni potpis chatkey ISPRAVAN");
						String messageChatKey = "";
						
						byte[] envelopeDecoded = Base64.getDecoder().decode(jsonToVerify.getString(MessageType.ENVELOPE).getBytes(StandardCharsets.UTF_8));
						byte[] envelopeDecrypted = CryptoImpl.asymmetricEncryptDecrypt(ChatClient.getInstance().getOpModeAsymmetric(), ChatClient.getInstance().getPrivateKeyPair().getPrivate(), envelopeDecoded, false);
						String envelopeDecryptedString = new String(Base64.getDecoder().decode(envelopeDecrypted), StandardCharsets.UTF_8);
						JSONObject jsonEnvoelope = new JSONObject(envelopeDecryptedString);
						String opModeSymmetric = jsonEnvoelope.getString(MessageType.ALGORITHM);
						String hashFunction = jsonEnvoelope.getString(MessageType.HASH);
						byte[] symmetricKey = Base64.getDecoder().decode(jsonEnvoelope.getString(MessageType.KEY).getBytes(StandardCharsets.UTF_8));
						//if client is not in the list, create new chat thread and add it to the list
						byte[] cipherDecoded = Base64.getDecoder().decode(jsonToVerify.getString(MessageType.CIPHER).getBytes(StandardCharsets.UTF_8));
						byte[] cipherDecrypted = CryptoImpl.symmetricEncryptDecrypt(opModeSymmetric, symmetricKey, cipherDecoded, false);
						byte[] cipherDecryptedDecoded = Base64.getDecoder().decode(cipherDecrypted);
						messageChatKey = new String(cipherDecryptedDecoded, StandardCharsets.UTF_8);
							
						if(ChatClient.getInstance().getRemoteClientsInCommunication().get(from) == null){		
							ChatClientThread cct = new ChatClientThread(from, messageChatKey);
							ChatClient.getInstance().getRemoteClientsInCommunication().put(from, cct);
							cct.setSymmetricKeyChat(symmetricKey);
							cct.setOpModeSymmetric(opModeSymmetric);
							cct.setHashFunction(hashFunction);
							cct.start();
						}else
							//else get the thread that already exists in the list
							ChatClient.getInstance().getRemoteClientsInCommunication().get(from).writeToHistory(from, messageChatKey);
					}
					else
						System.out.println("Digitalni potpis chatkey NIJE ispravan");
						
				} else {
					System.out.println("ChatClientReader nepoznat type poruke");
				}
			}
			
 
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Decrypt message.
	 * 
	 * @param message
	 * @return
	 */
	public String decryptMessage(String message){
		byte[] messageDecoded = Base64.getDecoder().decode(message.getBytes(StandardCharsets.UTF_8));
		byte[] messageDecrypt = CryptoImpl.symmetricEncryptDecrypt(ChatClient.getInstance().getOpModeSymmetric(), ChatClient.getInstance().getSymmetricKey(), messageDecoded, false);
		String messageString = new String(messageDecrypt, StandardCharsets.UTF_8);
		
		return messageString;
	}
}
