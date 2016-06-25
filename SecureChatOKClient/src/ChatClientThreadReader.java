import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.json.JSONException;
import org.json.JSONObject;

import secureLib.CryptoImpl;


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
				System.out.println("Reader gets request: " + request);
				
				JSONObject jsonObject = new JSONObject(request);
				String from = jsonObject.getString("from");
				String type = jsonObject.getString("type");
				String data = jsonObject.getString("data");
				
				if(type.equals("chat")){ 
					//remote client wants to chat, 
					//if(remoteClientsInCommunication.get(from) == null){
					if(ChatClient.getInstance().getRemoteClientsInCommunication().get(from) == null){
						//if client is not in the list, create new chat thread and add it to the list
						//ChatClientThread cct = new ChatClientThread(socket, from, username, data, remoteClientsInCommunication);
						ChatClientThread cct = new ChatClientThread(from, data);
						//remoteClientsInCommunication.put(from, cct);
						ChatClient.getInstance().getRemoteClientsInCommunication().put(from, cct);
						cct.start();
					}else
						//else get the thread that already exists in the list
						//remoteClientsInCommunication.get(from).writeToHistory(from, data);
						ChatClient.getInstance().getRemoteClientsInCommunication().get(from).writeToHistory(from, data);
				} else if (type.equals("updateUsers")) {
					//update users chat list
					//chatClient.updateListUsersGui(chatClient.stringToList(data,";"));
					ChatClient.getInstance().updateListUsersGui(ChatClient.getInstance().stringToList(data,";"));
				} else if (type.equals("server")){ 
					//message from server
					//remoteClientsInCommunication.get(from).writeToHistory(type, data);
					ChatClient.getInstance().getRemoteClientsInCommunication().get(from).writeToHistory(type, data);
				} else {
					System.out.println("ChatClientReader nepoznat type poruke");
				}
			}
			
		} catch (IOException | JSONException e) {
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
}
