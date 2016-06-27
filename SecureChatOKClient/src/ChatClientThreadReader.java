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

	//Socket socket;
	BufferedReader in;
	//String username;
	
	//remote clients to which this client is in communication with
	//ConcurrentHashMap<String, ChatClientThread> remoteClientsInCommunication = null;
	
	//included for update on JList list on gui, when new clients log in, gui JList is on this ChatClient thread
	//ChatClient chatClient;

	public ChatClientThreadReader(){//ChatClient chatClient){
		//this.socket = chatClient.getSocket();
		//this.username = chatClient.getUsername();
		//this.remoteClientsInCommunication = chatClient.getRemoteClientsInCommunication();
		//this.chatClient = chatClient;
		
	}
	
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
				//String to = jsonObject.getString("to");
				String from = jsonObject.getString("from");
				String type = jsonObject.getString("type");
				String data = jsonObject.getString("data");
				
				if(type.equals(MessageType.CHAT)){ 
					//remote client wants to chat, 
					if(ChatClient.getInstance().getRemoteClientsInCommunication().get(from) == null){
						//if client is not in the list, create new chat thread and add it to the list
						ChatClientThread cct = new ChatClientThread(from, data);
						ChatClient.getInstance().getRemoteClientsInCommunication().put(from, cct);
						cct.start();
					}else
						//else get the thread that already exists in the list
						ChatClient.getInstance().getRemoteClientsInCommunication().get(from).writeToHistory(from, data);
				} else if (type.equals(MessageType.UPDATE)) {
					//update users chat list
					ChatClient.getInstance().updateListUsersGui(ChatClient.getInstance().stringToList(data,";"));
				} else if (type.equals(MessageType.SERVER)){ 
					//message from server
					ChatClient.getInstance().getRemoteClientsInCommunication().get(from).writeToHistory(type, data);
				} else if (type.equals(MessageType.PUBLICKEY)){
					//System.out.println("To: " + to + "||| Public key: " + data);
					//potrebno da client koji je trazio javni kljuc saceka (wait) da kljuc stinge i nastavi nakon notify
					//mehanizam nalaze da se taj thread zakljuca u bloc synchronized na oba mjesta
					synchronized (ChatClient.getInstance().getRemoteClientsInCommunication().get(from)) {
						//PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(data.getBytes(StandardCharsets.UTF_8)));
						PublicKey publicKey = CryptoImpl.deserializeRsaPublicKey(data);
						ChatClient.getInstance().getRemoteClientsInCommunication().get(from).setRemotePublicKey(publicKey);
						ChatClient.getInstance().getRemoteClientsInCommunication().get(from).notify();
					}
				} else if (type.equals(MessageType.CHATKEY)) {
					System.out.println("chatkey request: " + data);
					//provjeriti da li je poruka ispravna verifikovati
					JSONObject jsonToVerify = new JSONObject(data);
					if(ChatClient.getInstance().getRemoteClientsInCommunication().get(from) == null){
						//if client is not in the list, create new chat thread and add it to the list
						ChatClientThread cct = new ChatClientThread(from, null);
						ChatClient.getInstance().getRemoteClientsInCommunication().put(from, cct);
						cct.requestRemoteClientPublicKey(from);

						//ceka da dobije javni kljuc ovja cct thread
						synchronized (cct) {
							cct.wait();
						}
						System.out.println("Stigaooooooooooooooooooooooooooooooooooooooo");
						PublicKey pubKey = cct.getRemotePublicKey();
//						if(pubKey != null)
//						if(CryptoImpl.verifyDigitalSignature(jsonToVerify.getString("cipher"), jsonToVerify.getString("envelope"), jsonToVerify.getString("digsig"), )){
//							
//							cct.start();
//						
//						}
					}
					
					// a zatim odgovoriti 
				} else if (type.equals(MessageType.CHATKEYOK)) {
					// odgovoro od chatkey treba da se ovdje uhvati kod drugog clienta
					// da se setuje symmetricKey izmedu dva client-a za secure chat komunikaciju
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
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
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
