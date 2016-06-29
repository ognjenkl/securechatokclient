import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.NoSuchPaddingException;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.bouncycastle.crypto.Digest;
import org.json.JSONException;
import org.json.JSONObject;

import secureLib.CryptoImpl;
import secureUtil.MessageType;

public class ChatClient {
	
	Socket socket;
	
	//this user's username
	String username;
	
	BufferedReader in;
	PrintWriter out;
	
	//users loggedin on server
	List<String> listChatUsersOnServer;
	
	//users loggedin on server and their public keys
	ConcurrentHashMap<String, PublicKey> usersAndPublicKeys;
	
	//list of users on server for chat, gui component
	JList<String> listUsersGui;
	//part of JList component for items change, becous JList cant change items
	DefaultListModel<String> listUsersGuiModel;
	
	String opModeAsymmetric = "";
	String opModeSymmetric = "";
	byte[] symmetricKey = null;
	
	KeyPair privateKeyPair = null;
	
	//remote clients as key to which this client's thread is in communication with
	ConcurrentHashMap<String, ChatClientThread> remoteClientsInCommunication;
	
	private static ChatClient chatClient = null;
	
	Properties properties = null;
	FileInputStream fis = null;
	String propIp = "";
	int propPort = 0;
	String propSymmetricOpModePaddingAes = "";
	String propSymmetricOpModePadding3Des = "";
	String propAsymmetricOpModePaddingRsa = "";
	String propServerPublicKeyPath = "";
	
    private ChatClient(){
    	try {
	    	//username = "o2";
	    	
	    	//System.out.println("Client: "+username);
	    	//listChatUsersOnServer = new ArrayList<String>();
	    	listChatUsersOnServer = new CopyOnWriteArrayList<>();
	    	
	    	listUsersGuiModel = new DefaultListModel<String>();
	    	listUsersGui = new JList<String>(listUsersGuiModel);
	    	remoteClientsInCommunication = new ConcurrentHashMap<String, ChatClientThread>();
	    	usersAndPublicKeys = new ConcurrentHashMap<>();
	    	
	    	properties = new Properties();
	    	File fileProperties = new File("resources/config.properties");
	    	if (fileProperties.exists()){
	    		fis = new FileInputStream(fileProperties);
				properties.load(fis);
				
				propIp = properties.getProperty("ip");
				propPort = Integer.parseInt(properties.getProperty("port"));
				propSymmetricOpModePaddingAes = properties.getProperty("symmetricOpModePaddingAes");
				propSymmetricOpModePadding3Des = properties.getProperty("symmetricOpModePadding3Des");
				propAsymmetricOpModePaddingRsa = properties.getProperty("asymmetricOpModePaddingRsa");
				propServerPublicKeyPath = properties.getProperty("serverPublicKeyPath");
			
				fis.close();
				
				
				
	    	} else {
	    		System.out.println("Ne postoji properties file");
	    	}
	    	
    	} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public static synchronized ChatClient getInstance(){
    	if(chatClient == null)
    		chatClient = new ChatClient();
    	
    	return chatClient;
    }
    
    public synchronized String getUsername() {
		return username;
	}

	public synchronized void setUsername(String username) {
		this.username = username;
	}
	
	public synchronized String getOpModeAsymmetric() {
		return opModeAsymmetric;
	}

	public synchronized void setOpModeAsymmetric(String opModeAsymmetric) {
		this.opModeAsymmetric = opModeAsymmetric;
	}

	public synchronized String getOpModeSymmetric() {
		return opModeSymmetric;
	}

	public synchronized void setOpModeSymmetric(String opModeSymmetric) {
		this.opModeSymmetric = opModeSymmetric;
	}
	
	public synchronized byte[] getSymmetricKey() {
		return symmetricKey;
	}

	public synchronized void setSymmetricKey(byte[] symmetricKey) {
		this.symmetricKey = symmetricKey;
	}

	public synchronized Socket getSocket() {
		return socket;
	}

	public synchronized void setSocket(Socket socket) {
		this.socket = socket;
	}
	
	public synchronized ConcurrentHashMap<String, ChatClientThread> getRemoteClientsInCommunication() {
		return remoteClientsInCommunication;
	}

	public synchronized void setRemoteClientsInCommunication(ConcurrentHashMap<String, ChatClientThread> remoteClientsInCommunication) {
		this.remoteClientsInCommunication = remoteClientsInCommunication;
	}

	public synchronized String getPropSymmetricOpModePaddingAes() {
		return propSymmetricOpModePaddingAes;
	}

	public synchronized void setPropSymmetricOpModePaddingAes(String propSymmetricOpModePaddingAes) {
		this.propSymmetricOpModePaddingAes = propSymmetricOpModePaddingAes;
	}

	public synchronized String getPropSymmetricOpModePadding3Des() {
		return propSymmetricOpModePadding3Des;
	}

	public synchronized void setPropSymmetricOpModePadding3Des(String propSymmetricOpModePadding3Des) {
		this.propSymmetricOpModePadding3Des = propSymmetricOpModePadding3Des;
	}

	public synchronized String getPropAsymmetricOpModePaddingRsa() {
		return propAsymmetricOpModePaddingRsa;
	}

	public synchronized void setPropAsymmetricOpModePaddingRsa(String propAsymmetricOpModePaddingRsa) {
		this.propAsymmetricOpModePaddingRsa = propAsymmetricOpModePaddingRsa;
	}

	public synchronized KeyPair getPrivateKeyPair() {
		return privateKeyPair;
	}

	public synchronized void setPrivateKeyPair(KeyPair privateKeyPair) {
		this.privateKeyPair = privateKeyPair;
	}

	public synchronized ConcurrentHashMap<String, PublicKey> getUsersAndPublicKeys() {
		return usersAndPublicKeys;
	}

	public synchronized void setUsersAndPublicKeys(ConcurrentHashMap<String, PublicKey> usersAndPublicKeys) {
		this.usersAndPublicKeys = usersAndPublicKeys;
	}

	public void startClient(){
		
		try {

			socket = new Socket(propIp, propPort);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
			
			startChatClientLoginGUI();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
	}


	public String login(String usern) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidAlgorithmParameterException{
		JSONObject jsonResp = null;
		try {			
			//json {"data":"og","from":"og","to":"s","type":"login"}
			
			String to = MessageType.SERVER;
			String from = usern;
			String type = MessageType.LOGIN;
			String data = usern;
			
			sendMessageLogin(to, from, type, data);
			
			String response = in.readLine();
			String responseDecrypt = decryptMessage(response);
			
			jsonResp = new JSONObject(responseDecrypt);
			//usersList = stringToList(jsonResp.getString("data"),";");
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		//return usersList;
		return jsonResp.toString();
	}
	
	public List<String> stringToList(String message, String delimiter){
		List<String> list = null;
		String[] parts = null;
		if (message.length() > 0 && message.contains(delimiter)){
			parts = message.split(delimiter);
			list = new CopyOnWriteArrayList<String>();
		}
		
		for (String part : parts)
			if(!part.equals(""))
				list.add(part);
		
		return list;
	}
	
	public void sendMessageLogin(String to, String from, String type, String data) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidAlgorithmParameterException{
		try {
	
			if(Math.random() < 0.5){
				opModeSymmetric = propSymmetricOpModePaddingAes ;
				symmetricKey = CryptoImpl.generateSecretKeyAES128();
			}else{
				 opModeSymmetric = propSymmetricOpModePadding3Des;
				 symmetricKey = CryptoImpl.generateDESede168Key();
			}
				
			opModeAsymmetric = propAsymmetricOpModePaddingRsa;
//			String opModeSymmetric = "AES/CBC/PKCS7Padding";
	//		String opModeSymmetric = "AES/ECB/PKCS7Padding";
	//		String opModeSymmetric = "DESede/ECB/PKCS7Padding";
	//		String opModeSymmetric = "DESede/CBC/PKCS7Padding";
			
			
			
			String publicKeyPath = propServerPublicKeyPath;
			PublicKey publicKeyServer = CryptoImpl.getPublicKey(publicKeyPath);
			//System.out.println("drugi public: "+publicKey.toString());

	
			
		//envelope
			byte[] symmetricKeyBase64 = Base64.getEncoder().encode(symmetricKey);
			String symmetricKeyString = new String(symmetricKeyBase64, StandardCharsets.UTF_8);
			
			//hash function just for login
			String hashFunctionLocal = "";

			if(Math.random() < 0.5){
				hashFunctionLocal = MessageType.SHA256;
			} else {
				hashFunctionLocal = MessageType.SHA512;
			}
			
			JSONObject jsonEnvelope = new JSONObject();
			jsonEnvelope.put(MessageType.KEY, symmetricKeyString);
			jsonEnvelope.put(MessageType.ALGORITHM, opModeSymmetric);
			jsonEnvelope.put(MessageType.HASH, hashFunctionLocal);
			
			System.out.println("client plain: " + jsonEnvelope.toString());
			
			byte[] digest = CryptoImpl.hash(hashFunctionLocal, jsonEnvelope.toString().getBytes(StandardCharsets.UTF_8));
			
			byte[] envelopeMaterial = jsonEnvelope.toString().getBytes(StandardCharsets.UTF_8);
			byte[] envelope = CryptoImpl.asymmetricEncryptDecrypt(opModeAsymmetric, publicKeyServer, envelopeMaterial, true);
			byte[] envelopeEncoded = Base64.getEncoder().encode(envelope); 
			String envelopeString = new String(envelopeEncoded, StandardCharsets.UTF_8);
		//end envelope

		//cipher
			
			
		//end cipher
			
		//digital signature
			
			
		//end digital signature
			
			JSONObject jsonMessage = new JSONObject();
			jsonMessage.put(MessageType.ENVELOPE, envelopeString);
			jsonMessage.put(MessageType.DIGSIG, value);
			jsonMessage.put(MessageType.CIPHER, value)
			
			out.println(envelopeString);

			
			String response = "";
			String predefinedOKTag = MessageType.lOGINOK;
			
			//server response
			response = in.readLine();
			
			byte[] responseDecoded = Base64.getDecoder().decode(response.getBytes(StandardCharsets.UTF_8));
			byte[] responseDecrypt = CryptoImpl.symmetricEncryptDecrypt(opModeSymmetric, symmetricKey, responseDecoded, false);
			String responseString = new String(responseDecrypt, StandardCharsets.UTF_8);
			if(responseString.equals(predefinedOKTag)){
				//System.out.println("Predefined OK: " + requestString);
				
				JSONObject jsonObj = new JSONObject();
				jsonObj.put("to", to);
				jsonObj.put("from", from);
				jsonObj.put("type", type);
				jsonObj.put("data", data);
				
				System.out.println("client sent json (prencrypted): " + jsonObj.toString() );
				byte[] cipher = CryptoImpl.symmetricEncryptDecrypt(opModeSymmetric, symmetricKey, jsonObj.toString().getBytes(StandardCharsets.UTF_8), true);
				byte[] cipherEncoded = Base64.getEncoder().encode(cipher);
				String cipherString = new String(cipherEncoded, StandardCharsets.UTF_8);
				
				out.println(cipherString);				
			}
			
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		byte[] messageDecrypt = CryptoImpl.symmetricEncryptDecrypt(opModeSymmetric, symmetricKey, messageDecoded, false);
		String messageString = new String(messageDecrypt, StandardCharsets.UTF_8);
		
		return messageString;
	}
	
	public void startChatClientLoginGUI(){
		JFrame frameLogin = new JFrame("Chat Login");
		JPanel panelLogin = new JPanel();
		JLabel labelUser = new JLabel("Username: ");
		JTextField usernameTextField = new JTextField();
		JLabel labelPass = new JLabel("Password: ");
		JPasswordField passwordField = new JPasswordField();
		JLabel loginError = new JLabel("");
		JButton loginButton = new JButton("Login");
		
		frameLogin.setSize(400, 300);
		frameLogin.setLocation(100, 100);
		frameLogin.toFront();
		frameLogin.setResizable(false);
		
		int componentsLeftPadding = 100;
		panelLogin.setLayout(null);
		frameLogin.add(panelLogin);
		labelUser.setBounds(componentsLeftPadding, 20, 100, 30);
		panelLogin.add(labelUser);
		usernameTextField.setBounds(componentsLeftPadding, 50, 200, 30);
		panelLogin.add(usernameTextField);
		labelPass.setBounds(componentsLeftPadding, 80, 100, 30);
		panelLogin.add(labelPass);
		passwordField.setBounds(componentsLeftPadding, 110, 200, 30);
		panelLogin.add(passwordField);
		loginError.setBounds(componentsLeftPadding, 150, 200, 20);
		panelLogin.add(loginError);
		loginButton.setBounds(componentsLeftPadding, 180, 200, 80);
		panelLogin.add(loginButton);
		
		loginButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					
					username = usernameTextField.getText();
					
					File privateKeyPairFile = new File("pki/" + username + "2048.key");
					
					if(privateKeyPairFile.exists()){
						privateKeyPair = CryptoImpl.getKeyPair(privateKeyPairFile);
					
					
						//login - get a list of all logged users and their public keys or null
						String str = login(username);
						
						JSONObject jsonLogin = new JSONObject(str);
						JSONObject jsonLoginData = new JSONObject(jsonLogin.getString("data"));
						listChatUsersOnServer = stringToList(jsonLoginData.getString("clients"), ";");
						
						System.out.println("Client: " + username );
						if(listChatUsersOnServer != null){
							
							loginError.setText("");
							System.out.println("json login data: " + jsonLoginData.toString());
							startChatClientGUI(jsonLoginData.toString());
							
							ChatClientThreadReader cctr = new ChatClientThreadReader();
							cctr.start();
							
							frameLogin.setVisible(false);
						} else {
							System.out.println("Conncection failed!");
							loginError.setText("Login failed!");
						}
					} else {
						System.out.println("Nema privatnog kljuca na putanji: " + privateKeyPairFile.getPath());
						loginError.setText("Neuspjesno logovanje");
					}
					
					
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (InvalidKeyException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (NoSuchAlgorithmException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (NoSuchPaddingException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (InvalidKeySpecException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (InvalidAlgorithmParameterException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (JSONException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} 
			}
		});
		
		frameLogin.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frameLogin.setVisible(true);
	}

	public synchronized void startChatClientGUI(String clientsWithPubKeys){
		JFrame frameChatClientGUI = new JFrame("Chat Client");
		JPanel panelChatClient = new JPanel();
		JLabel welcomeLabel = new JLabel("Welcome: "+username);
		//listUsersGui = new JList<String>(clients.toArray(new String[clients.size()]));
		updateListUsersGui(clientsWithPubKeys);
		JButton buttonStartChat = new JButton("Start Chat");
		
		frameChatClientGUI.setSize(600, 600);
		//frameChatClientGUI.setLocation(200, 200);
		frameChatClientGUI.setLocation((new Random().nextInt(10)+1)*100, 200);
		frameChatClientGUI.setResizable(false);
		frameChatClientGUI.toFront();
		panelChatClient.setLayout(null);
		frameChatClientGUI.add(panelChatClient);
		
		
		welcomeLabel.setBounds(50, 20, 200, 20);
		panelChatClient.add(welcomeLabel); 
		listUsersGui.setBounds(50, 50, 300, 300);
		panelChatClient.add(listUsersGui);
		buttonStartChat.setBounds(50, 430, 200, 80);
		panelChatClient.add(buttonStartChat);
		
		buttonStartChat.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if(listUsersGuiModel != null && listUsersGuiModel.size() > 1){
					String remoteUser = listUsersGui.getSelectedValue();
					ChatClientThread cct = new ChatClientThread( remoteUser , null);
					remoteClientsInCommunication.put(remoteUser, cct);

					cct.setRemotePublicKey(usersAndPublicKeys.get(remoteUser));
					cct.start();
				
				}
				
			}
		});
		
		frameChatClientGUI.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frameChatClientGUI.setVisible(true);
		
	}
	
	public synchronized void updateListUsersGui(String clientsWithPubKeys){
		
		listUsersGuiModel.clear();
		JSONObject jsonObj;
		try {
			jsonObj = new JSONObject(clientsWithPubKeys);
			
			List<String> users = stringToList(jsonObj.getString("clients"), ";");
			for(String user : users){
				listUsersGuiModel.addElement(user);
				PublicKey pubKey =  CryptoImpl.deserializeRsaPublicKey(jsonObj.getString(user));
				usersAndPublicKeys.put(user, pubKey);
			}
			
			System.out.println("updated gui for: " + username);
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		
		ChatClient.getInstance().startClient();

	}
	


	
}
