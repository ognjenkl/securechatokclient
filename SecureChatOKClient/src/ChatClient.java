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

import org.json.JSONException;
import org.json.JSONObject;

import secureLib.CryptoImpl;
import utilLib.MessageType;

public class ChatClient {
	
	Socket socket;
	
	//this user's username
	String username;
	
	BufferedReader in;
	PrintWriter out;
	
	//users logedin on server
	List<String> listChatUsersOnServer;
	
	//list of users on server for chat, gui component
	JList<String> listUsersGui;
	//part of JList component for items change, becous JList cant change items
	DefaultListModel<String> listUsersGuiModel;
	
	String opModeAsymmetric = "";
	String opModeSymmetric = "";
	byte[] symmetricKey = null;
	
	//remote clients to which this client is in communication with
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
    
    public static ChatClient getInstance(){
    	if(chatClient == null)
    		chatClient = new ChatClient();
    	
    	return chatClient;
    }
    
    public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}
	
	public String getOpModeAsymmetric() {
		return opModeAsymmetric;
	}

	public void setOpModeAsymmetric(String opModeAsymmetric) {
		this.opModeAsymmetric = opModeAsymmetric;
	}

	public String getOpModeSymmetric() {
		return opModeSymmetric;
	}

	public void setOpModeSymmetric(String opModeSymmetric) {
		this.opModeSymmetric = opModeSymmetric;
	}
	
	public byte[] getSymmetricKey() {
		return symmetricKey;
	}

	public void setSymmetricKey(byte[] symmetricKey) {
		this.symmetricKey = symmetricKey;
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

	public Socket getSocket() {
		return socket;
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}

	public List<String> login(String usern) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidAlgorithmParameterException{
		List<String> usersList = null;
		try {			
			//json {"data":"og","from":"og","to":"s","type":"login"}
			
			String to = MessageType.SERVER;
			String from = usern;
			String type = MessageType.LOGIN;
			String data = usern;
			
			sendMessageLogin(to, from, type, data);
			
			String response = in.readLine();
			String responseDecrypt = receiveMessage(response);
			
			usersList = stringToList(responseDecrypt,";");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		return usersList;

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
				
			String opModeAsymmetric = propAsymmetricOpModePaddingRsa;
			
//			String opModeSymmetric = "AES/CBC/PKCS7Padding";
	//		String opModeSymmetric = "AES/ECB/PKCS7Padding";
	//		String opModeSymmetric = "DESede/ECB/PKCS7Padding";
	//		String opModeSymmetric = "DESede/CBC/PKCS7Padding";
			
			
			
			String publicKeyPath = propServerPublicKeyPath;
			String privateKeyPath = "pki/" + username + "2048.key";
			//String absoluteKeyPath = System.getProperty("user.dir") + "\\pki\\og2048.pem";
			KeyPair privateKeyPair = CryptoImpl.getKeyPair(privateKeyPath);
			PublicKey publicKey = CryptoImpl.getPublicKey(publicKeyPath);
			
			
			byte[] symmetricKeyBase64 = Base64.getEncoder().encode(symmetricKey);
			String symmetricKeyString = new String(symmetricKeyBase64, StandardCharsets.UTF_8);
			
			JSONObject jsonEnvelope = new JSONObject();
			jsonEnvelope.put(MessageType.KEY, symmetricKeyString);
			jsonEnvelope.put(MessageType.ALGORITHM, opModeSymmetric);
			System.out.println("client plain: " + jsonEnvelope.toString());
			
			byte[] envelopeMaterial = jsonEnvelope.toString().getBytes(StandardCharsets.UTF_8);
			byte[] envelope = CryptoImpl.asymmetricEncryptDecrypt(opModeAsymmetric, publicKey, envelopeMaterial, true);
			byte[] envelopeEncoded = Base64.getEncoder().encode(envelope); 
			String envelopeString = new String(envelopeEncoded, StandardCharsets.UTF_8);
			
			out.println(envelopeString);

			
			String request = "";
			String predefinedOKTag = MessageType.OK;
			request = in.readLine();
			byte[] requestDecoded = Base64.getDecoder().decode(request.getBytes(StandardCharsets.UTF_8));
			byte[] requestDecrypt = CryptoImpl.symmetricEncryptDecrypt(opModeSymmetric, symmetricKey, requestDecoded, false);
			String requestString = new String(requestDecrypt, StandardCharsets.UTF_8);
			if(requestString.equals(predefinedOKTag)){
				System.out.println("Predefined OK: " + requestString);
				
				JSONObject jsonObj = new JSONObject();
				jsonObj.put("to", to);
				jsonObj.put("from", from);
				jsonObj.put("type", type);
				jsonObj.put("data", data);
				
				System.out.println("client sent json: " + jsonObj.toString() );
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
	 * Receive message and decrypt.
	 * @param message
	 * @return
	 */
	public String receiveMessage(String message){
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
				try{
					
					username = usernameTextField.getText();
					
					//get a list of all logged users or null
					listChatUsersOnServer = login(username);
					System.out.println("Client: " + username );
					if(listChatUsersOnServer != null){
						
						loginError.setText("");
						ChatClientThreadReader cctr = new ChatClientThreadReader();
						
						cctr.start();
						startChatClientGUI(listChatUsersOnServer);
						frameLogin.setVisible(false);
					}
					else {
						System.out.println("Conncection failed");
						loginError.setText("Login failed");
					}
				} catch (Exception ex){
					System.out.println("Login failed");
					loginError.setText("Login failed");
				}
			}
		});
		
		frameLogin.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frameLogin.setVisible(true);
	}
	
	
	public ConcurrentHashMap<String, ChatClientThread> getRemoteClientsInCommunication() {
		return remoteClientsInCommunication;
	}

	public void setRemoteClientsInCommunication(ConcurrentHashMap<String, ChatClientThread> remoteClientsInCommunication) {
		this.remoteClientsInCommunication = remoteClientsInCommunication;
	}

	public void startChatClientGUI(List<String> clients){
		JFrame frameChatClientGUI = new JFrame("Chat Client");
		JPanel panelChatClient = new JPanel();
		JLabel welcomeLabel = new JLabel("Welcome: "+username);
		//listUsersGui = new JList<String>(clients.toArray(new String[clients.size()]));
		updateListUsersGui(clients);
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
					cct.start();
				}
				
			}
		});
		
		frameChatClientGUI.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frameChatClientGUI.setVisible(true);
		
	}
	
	public synchronized void updateListUsersGui(List<String> users){
		listUsersGuiModel.clear();
		for(String user : users)
			listUsersGuiModel.addElement(user);
		System.out.println("update usernama: " + username);
	}
	
	
	
	public static void main(String[] args) {
		
		ChatClient.getInstance().startClient();

	}
	
	
}
