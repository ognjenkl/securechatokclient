import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Random;

import javax.crypto.NoSuchPaddingException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.json.JSONException;
import org.json.JSONObject;

import secureLib.CryptoImpl;
import secureUtil.MessageType;


/**
 * Client's thread for secure chat communication to other clients.
 * 
 * @author ognjen
 *
 */
public class ChatClientThread extends Thread {

	PrintWriter out;
	
	JFrame frame;
	JPanel panel;
	JTextField textField;
	JTextField messageTextField;
	JTextArea messageHistory;
	JButton send;
	JLabel labelFrom;
	JLabel labelTo;
	
	//String localClient;
	String remoteClient;
	String message;
	BufferedReader in;
	
	/**
	 * Public key of remote client in secure chat communication.
	 */
	PublicKey remotePublicKey = null;
	
	/**
	 * Symmetric (secret) key for chat communication.
	 */
	byte[] symmetricKeyChat = null;
	String opModeSymmetric = "";
	
	/**
	 * Hash function
	 */
	String hashFunction = "";
	
	
	
	public ChatClientThread( String remoteClient, String message){
		this.remoteClient = remoteClient;
		this.message = message;
		try {
			in = new BufferedReader(new InputStreamReader(ChatClient.getInstance().getSocket().getInputStream()));
			out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(ChatClient.getInstance().getSocket().getOutputStream())),true);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void run(){

		if(frame == null)
				startGUI();
	}
	
	
	/**
	 * 
	 */
	//public void startGUI(String localUser, String remoteUser){
	public synchronized void startGUI(){
		
		frame = new JFrame("ChatClientThreadGUI");
		panel = new JPanel();
		textField = new JTextField();
		messageHistory = new JTextArea();
		messageTextField = new JTextField();
		send = new JButton("Send");
		//labelFrom = new JLabel("User: " + localClient);
		labelFrom = new JLabel("User: " + ChatClient.getInstance().getUsername());
		labelTo = new JLabel("Remote user: " + remoteClient);
		
        
        frame.setSize(500, 500);
        frame.setLocation((new Random().nextInt(10)+1)*100, 200);
        frame.setResizable(false);

        panel.setLayout(null);
        frame.add(panel);
        labelFrom.setBounds(20, 10, 200, 20);
        panel.add(labelFrom);
        labelTo.setBounds(20, 30, 200, 20);
        panel.add(labelTo);
        messageHistory.setBounds(20, 50, 450, 340);
        messageHistory.setEditable(false);
        if(message != null)
        	writeToHistory(remoteClient, message);
        panel.add(messageHistory);
        messageTextField.setBounds(20, 400, 340, 30);
        panel.add(messageTextField);
        send.setBounds(375, 400, 95, 30);
        panel.add(send);
        
        
        
        // Add Listeners
        send.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	if(e.getSource() == send && !messageTextField.getText().equals("")){
            		try{
            			message = messageTextField.getText();
                		
                		if(symmetricKeyChat == null){
                			if(Math.random() < 0.5){
                				opModeSymmetric = ChatClient.getInstance().getPropSymmetricOpModePaddingAes();
                				symmetricKeyChat = CryptoImpl.generateSecretKeyAES128();
                			} else {
                				 opModeSymmetric = ChatClient.getInstance().getPropSymmetricOpModePadding3Des();
                				 symmetricKeyChat = CryptoImpl.generateDESede168Key();
                			}
                			
                			String tempSymmetricKeyEncodedString = new String( Base64.getEncoder().encode(symmetricKeyChat),StandardCharsets.UTF_8);
                			
                			byte[] cipher = CryptoImpl.symmetricEncryptDecrypt(opModeSymmetric, symmetricKeyChat, Base64.getEncoder().encode(message.getBytes(StandardCharsets.UTF_8)), true);
                			String cipherString = new String(Base64.getEncoder().encode(cipher), StandardCharsets.UTF_8);

                			//digest
               				if(Math.random() < 0.5){
                				hashFunction = MessageType.SHA256;
                			} else {
                				hashFunction = MessageType.SHA512;
                			}
                			byte[] digest = CryptoImpl.hash(hashFunction, message.getBytes(StandardCharsets.UTF_8));
                			
                			byte[] digitalSignatur = CryptoImpl.asymmetricEncryptDecrypt(ChatClient.getInstance().getOpModeAsymmetric(), ChatClient.getInstance().getPrivateKeyPair().getPrivate(), digest, true);
                			String digitalSignaturString = new String(Base64.getEncoder().encode(digitalSignatur), StandardCharsets.UTF_8);
                			
                			JSONObject jsonChatKey = new JSONObject();
                			jsonChatKey.put(MessageType.KEY, tempSymmetricKeyEncodedString);
                			jsonChatKey.put(MessageType.ALGORITHM, opModeSymmetric);
                			jsonChatKey.put(MessageType.HASH, hashFunction);
                			byte[] jsonChatKeyEncoded = Base64.getEncoder().encode(jsonChatKey.toString().getBytes(StandardCharsets.UTF_8));
     
                			//envelope
                			byte[] envelope = CryptoImpl.asymmetricEncryptDecrypt(ChatClient.getInstance().getOpModeAsymmetric(), remotePublicKey, jsonChatKeyEncoded, true);
                			String envelopeString = new String(Base64.getEncoder().encode(envelope), StandardCharsets.UTF_8);
                			
                			JSONObject jsonMessage = new JSONObject();
                			jsonMessage.put(MessageType.ENVELOPE, envelopeString);
                			jsonMessage.put(MessageType.DIGSIG, digitalSignaturString);
                			jsonMessage.put(MessageType.CIPHER, cipherString);
                			
                			sendMessage(remoteClient, ChatClient.getInstance().getUsername(), MessageType.CHATKEY, jsonMessage.toString());
                		} else{
                			
                			byte[] cipher = CryptoImpl.symmetricEncryptDecrypt(opModeSymmetric, symmetricKeyChat, Base64.getEncoder().encode(message.getBytes(StandardCharsets.UTF_8)), true);
                			String cipherString = new String(Base64.getEncoder().encode(cipher), StandardCharsets.UTF_8);
                			
                			//digest
                			byte[] digest = null;
                			digest = CryptoImpl.hash(hashFunction, message.getBytes(StandardCharsets.UTF_8));
                			
                			//digital signature
                			byte[] digitalSignatur = CryptoImpl.asymmetricEncryptDecrypt(ChatClient.getInstance().getOpModeAsymmetric(), ChatClient.getInstance().getPrivateKeyPair().getPrivate(), digest, true);
                			String digitalSignaturString = new String(Base64.getEncoder().encode(digitalSignatur), StandardCharsets.UTF_8);
     
                			JSONObject jsonMessage = new JSONObject();
                			jsonMessage.put(MessageType.DIGSIG, digitalSignaturString);
                			jsonMessage.put(MessageType.CIPHER, cipherString);
                			
                			sendMessage(remoteClient, ChatClient.getInstance().getUsername(), MessageType.CHAT, jsonMessage.toString());
                		
                		}
                		
                		writeToHistory(ChatClient.getInstance().getUsername(), message);
                		messageTextField.setText("");
            			
            		} catch (JSONException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
            	}
            }
        });
        
        // on window close remove itself form list of remote clients, it's indicator if chat window is opened
        frame.addWindowListener(new WindowAdapter() {
        	@Override
        	public void windowClosing(WindowEvent e){
        		//remoteClientsInCommunication.remove(remoteClient);
        		ChatClient.getInstance().getRemoteClientsInCommunication().remove(remoteClient);
        	}
        	
        	@Override
        	public void windowOpened(WindowEvent e){
    				messageTextField.requestFocus();
    			}
   
		});
        
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
        frame.toFront();
        
	}
	
	
	
	public synchronized PublicKey getRemotePublicKey() {
		return remotePublicKey;
	}

	public synchronized void setRemotePublicKey(PublicKey remotePublicKey) {
		this.remotePublicKey = remotePublicKey;
	}

	public byte[] getSymmetricKeyChat() {
		return symmetricKeyChat;
	}

	public void setSymmetricKeyChat(byte[] symmetricKeyChat) {
		this.symmetricKeyChat = symmetricKeyChat;
	}

	public synchronized String getOpModeSymmetric() {
		return opModeSymmetric;
	}

	public synchronized void setOpModeSymmetric(String opModeSymmetric) {
		this.opModeSymmetric = opModeSymmetric;
	}

	public synchronized String getHashFunction() {
		return hashFunction;
	}

	public synchronized void setHashFunction(String hashFunction) {
		this.hashFunction = hashFunction;
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
	
	/**
	 * Encrypts message for server and sends symmetric encrypted message to server.
	 * 
	 * @param to
	 * @param from
	 * @param type
	 * @param data
	 */
	public void sendMessage(String to, String from, String type, String data){
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put(MessageType.TO, to);
			jsonObj.put(MessageType.FROM, from);
			jsonObj.put(MessageType.TYPE, type);
			jsonObj.put(MessageType.DATA, data);
			
			byte[] cipher = CryptoImpl.symmetricEncryptDecrypt(ChatClient.getInstance().getOpModeSymmetric(), ChatClient.getInstance().getSymmetricKey(), jsonObj.toString().getBytes(StandardCharsets.UTF_8), true);
			byte[] cipherEncoded = Base64.getEncoder().encode(cipher);
			String cipherString = new String(cipherEncoded, StandardCharsets.UTF_8);
			
			out.println(cipherString);

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Sends chat message.
	 * 
	 * @param to
	 * @param from
	 * @param type
	 * @param data
	 */
	public void sendMessageChat(String to, String from, String type, String data){

		if(symmetricKeyChat == null){
			if(Math.random() < 0.5){
				opModeSymmetric = ChatClient.getInstance().getPropSymmetricOpModePaddingAes() ;
				symmetricKeyChat = CryptoImpl.generateSecretKeyAES128();
			}else{
				 opModeSymmetric = ChatClient.getInstance().getPropSymmetricOpModePadding3Des();
				 symmetricKeyChat = CryptoImpl.generateDESede168Key();
			}
		}
		
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put(MessageType.TO, to);
			jsonObj.put(MessageType.FROM, from);
			jsonObj.put(MessageType.TYPE, type);
			jsonObj.put(MessageType.DATA, data);
			
			byte[] cipher = CryptoImpl.symmetricEncryptDecrypt(ChatClient.getInstance().getOpModeSymmetric(), ChatClient.getInstance().getSymmetricKey(), jsonObj.toString().getBytes(StandardCharsets.UTF_8), true);
			byte[] cipherEncoded = Base64.getEncoder().encode(cipher);
			String cipherString = new String(cipherEncoded, StandardCharsets.UTF_8);
			
			out.println(cipherString);

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void writeToHistory(String user, String message){
		messageHistory.append(user + ":" + message + "\n" );
	}
	
	public void requestRemoteClientPublicKey(String user) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidAlgorithmParameterException, IOException {
		sendMessage(user, ChatClient.getInstance().getUsername(), MessageType.PUBLICKEY, user);
	}

}
