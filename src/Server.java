import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import spread.AdvancedMessageListener;
import spread.MembershipInfo;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;

public class Server {
	
	// The Spread Connection.
	private SpreadConnection connection;
	
	// The keyboard input.
	private InputStreamReader inputKeyboard;

	// The group of the server.
	private SpreadGroup group;
	
	// Number of members inside the group.
	private int groupSize;
	
	// The name of the server.
	String serverName;
	
	// The group name of the Clients.
	private String clientsGroup;
	
	// The group name of the Slaves.
	private String slavesGroup;
	
	// The priority of the server.
	private int priority;
	
	// The server name of the Master.
	private String master;
	
	// Structure to store the priority of all server in the group.
	private Map<String, Integer> tmServers;
	
	// Structure to store the the slaves/files.
	private Map<String, ArrayList<String>> hmSlavesAndFiles;
	
	// Counts the number of votes received to elect this server as Master.
	private int votes;
	
	public Server(String address, int port, String server, int priority, String group) {
		
		if (Integer.parseInt(server.split("_")[1]) >= 0 &&
				Integer.parseInt(server.split("_")[1]) <= 1000) {
			
			// Setup the keyboard input.
			this.inputKeyboard = new InputStreamReader(System.in);
			
			// Establish the spread connection.
			try {
				
				this.connection = new SpreadConnection();
				this.connection.connect(InetAddress.getByName(address), port, server, false, true);
				
			} catch(SpreadException e) {
				
				System.err.println("There was an error connecting to the daemon.");
				
				e.printStackTrace();
				
				System.exit(1);
				
			} catch(UnknownHostException e) {
				
				System.err.println("Can't find the daemon " + address);
				
				e.printStackTrace();
				
				System.exit(1);
				
			}
			
			// Set the variables
			this.serverName = server;
			this.priority = priority;

			this.group = new SpreadGroup();
			try {
				
				this.group.join(this.connection, group);
				
			} catch (SpreadException e) {
				
				e.printStackTrace();
				
			}
			
			this.master = null;
			this.clientsGroup = "clients";
			this.slavesGroup = "slaves";
			this.tmServers = new TreeMap<String, Integer>();
			this.hmSlavesAndFiles = new HashMap<String, ArrayList<String>>();
			
			// Add the listeners
			this.connection.add(new AdvancedMessageListener() {
				
				@Override
				public void regularMessageReceived(SpreadMessage message) {
					
					byte data[] = message.getData();
					String dataMessage = new String(data);
					String splittedMessage[];
					
					// Message to verify the Master
					if (message.getType() == 0) {
						
						splittedMessage = dataMessage.split("&");
						
						updateMaster(splittedMessage[1], splittedMessage[2], splittedMessage[3]);
						
					}
					
					// Message sent from slaves to populate the hash of slaves/files only with slaves
					else if (message.getType() == 1) {
						
						splittedMessage = dataMessage.split("&");
						
						if (splittedMessage[1].equals("join"))
							hmSlavesAndFiles.put(splittedMessage[2], new ArrayList<String>());
						
						else if (splittedMessage[1].equals("leave") || splittedMessage[1].equals("disconnect"))
							hmSlavesAndFiles.remove(splittedMessage[2]);
						
					}
					
					// Message sent from slaves to populate the hash of slaves/files with files
					else if (message.getType() == 2) {
						
						splittedMessage = dataMessage.split("&");
						ArrayList<String> files;
						
						if (splittedMessage[1].equals("loadfiles")) {
							
							if (splittedMessage.length == 4) {
								
								files = new ArrayList<String>(Arrays.asList(splittedMessage[3].split("#")));
								
								ArrayList<String> filesToDelete = new ArrayList<String>();
								
								for (String key : hmSlavesAndFiles.keySet())
									if (!key.equals(splittedMessage[2]))
										for (String file : hmSlavesAndFiles.get(key))
											if (files.contains(file))
												filesToDelete.add(file);
								
								files.removeAll(filesToDelete);
								
								hmSlavesAndFiles.put(splittedMessage[2], files);
								
								String strFilesToDelete = "";
								
								for (int i = 0; i < filesToDelete.size(); i++)
									if (i > 0)
										strFilesToDelete += "#" + filesToDelete.get(i);
									else
										strFilesToDelete += filesToDelete.get(i);
								
								SpreadMessage deleteFileMessage = new SpreadMessage();
								deleteFileMessage.setSafe();
								deleteFileMessage.setType((short) 1);
								deleteFileMessage.addGroup(slavesGroup);
								deleteFileMessage.setData(new String("&deletefile&" + splittedMessage[2] + "&" + strFilesToDelete).getBytes());
								
								try {
									
									connection.multicast(deleteFileMessage);
									
								} catch (SpreadException e) {
									
									e.printStackTrace();
									
								}
									
							}
							
						}
						
						else if (splittedMessage[1].equals("addfile")) {
							
							String slave = splittedMessage[2];
							String filename = splittedMessage[3];
							
							files = hmSlavesAndFiles.get(slave);
							files.add(filename);
							
							hmSlavesAndFiles.put(slave, files);
								
						}

						else if (splittedMessage[1].equals("deletefile")) {
							
							files = hmSlavesAndFiles.get(splittedMessage[2]);
							files.remove(splittedMessage[3]);
							
							hmSlavesAndFiles.put(splittedMessage[2], files);
							
						}
						
					}
					
					// Requests sent from clients
					else if (message.getType() == 3) {
						
						if (serverName.equals(master)) {
							
							splittedMessage = dataMessage.split("&");
							
							String instruction = splittedMessage[1];
							String client = splittedMessage[2];
							
							if (instruction.equals("create")) {
								
								String fileToCreate = splittedMessage[3];
								boolean exists = false;
								
								for (String key : hmSlavesAndFiles.keySet())
									if (hmSlavesAndFiles.get(key).contains(fileToCreate)) {
										
										SpreadMessage errorCreateMessage = new SpreadMessage();
										errorCreateMessage.setSafe();
										errorCreateMessage.setType((short) 1);
										errorCreateMessage.addGroup(clientsGroup);
										errorCreateMessage.setData(new String("&create&" + client + "&" + fileToCreate + "&" + master).getBytes());
										
										try {
											
											connection.multicast(errorCreateMessage);
											
										} catch (SpreadException e) {
											
											e.printStackTrace();
											
										}
										
										exists = true;
										
										break;
										
									}
									
								if (!exists) {
								
									Random generator = new Random();
									int index = generator.nextInt(hmSlavesAndFiles.keySet().size());
									
									String slave = hmSlavesAndFiles.keySet().toArray(new String[hmSlavesAndFiles.keySet().size()])[index];
									
									SpreadMessage createFileMessage = new SpreadMessage();
									createFileMessage.setSafe();
									createFileMessage.setType((short) 0);
									createFileMessage.addGroup(slavesGroup);
									createFileMessage.setData(new String("&create&" + splittedMessage[2] + "&" + splittedMessage[3] + "&" + slave).getBytes());
									
									try {
										
										connection.multicast(createFileMessage);
										
									} catch (SpreadException e) {
										
										e.printStackTrace();
										
									}
									
								}
								
							} else if (instruction.equals("read")) {
								
								String fileToRead = splittedMessage[3];
								String slave = "";
								boolean exists = false;
								
								for (String key : hmSlavesAndFiles.keySet())
									if (hmSlavesAndFiles.get(key).contains(fileToRead)) {
										
										slave = key;
										
										exists = true;
										
										break;
										
									}
									
								if (exists) {
									
									SpreadMessage readFileMessage = new SpreadMessage();
									readFileMessage.setSafe();
									readFileMessage.setType((short) 0);
									readFileMessage.addGroup(slavesGroup);
									readFileMessage.setData(new String("&read&" + splittedMessage[2] + "&" + fileToRead + "&" + slave).getBytes());
									
									try {
										
										connection.multicast(readFileMessage);
										
									} catch (SpreadException e) {
										
										e.printStackTrace();
										
									}
									
								} else {
									
									SpreadMessage errorReadMessage = new SpreadMessage();
									errorReadMessage.setSafe();
									errorReadMessage.setType((short) 1);
									errorReadMessage.addGroup(clientsGroup);
									errorReadMessage.setData(new String("&read&" + client + "&" + fileToRead + "&" + master).getBytes());
									
									try {
										
										connection.multicast(errorReadMessage);
										
									} catch (SpreadException e) {
										
										e.printStackTrace();
										
									}
									
								}
								
							} else if (instruction.equals("write")) {
								
								String fileToWrite = splittedMessage[3];
								String slave = "";
								boolean exists = false;
								
								for (String key : hmSlavesAndFiles.keySet())
									if (hmSlavesAndFiles.get(key).contains(fileToWrite)) {
										
										slave = key;
										
										exists = true;
										
										break;
									
									}
								
								if (exists) {
									
									SpreadMessage writeFileMessage = new SpreadMessage();
									writeFileMessage.setSafe();
									writeFileMessage.setType((short) 0);
									writeFileMessage.addGroup(slavesGroup);
									writeFileMessage.setData(new String("&write&" + splittedMessage[2] + "&" + fileToWrite + "&" + slave).getBytes());
									
									try {
										
										connection.multicast(writeFileMessage);
										
									} catch (SpreadException e) {
										
										e.printStackTrace();
									}
									
								} else {
									
									SpreadMessage errorWriteMessage = new SpreadMessage();
									errorWriteMessage.setSafe();
									errorWriteMessage.setType((short) 1);
									errorWriteMessage.addGroup(clientsGroup);
									errorWriteMessage.setData(new String("&write&" + client + "&" + fileToWrite + "&" + master).getBytes());
									
									try {
										
										connection.multicast(errorWriteMessage);
										
									} catch (SpreadException e) {
										
										e.printStackTrace();
										
									}
									
								}
								
							} else if (instruction.equals("delete")) {
								
								String fileToDelete = splittedMessage[3];
								String slave = "";
								boolean exists = false;
								
								for (String key : hmSlavesAndFiles.keySet())
									if (hmSlavesAndFiles.get(key).contains(fileToDelete)) {
										
										slave = key;
										
										exists = true;
										
										break;
										
									}
										
								if (exists) {
								
									SpreadMessage deleteFileMessage = new SpreadMessage();
									deleteFileMessage.setSafe();
									deleteFileMessage.setType((short) 0);
									deleteFileMessage.addGroup(slavesGroup);
									deleteFileMessage.setData(new String("&delete&" + splittedMessage[2] + "&" + fileToDelete + "&" + slave).getBytes());
									
									try {
										
										connection.multicast(deleteFileMessage);
										
									} catch (SpreadException e) {
										
										e.printStackTrace();
										
									}
									
								} else {
									
									SpreadMessage errorDeleteMessage = new SpreadMessage();
									errorDeleteMessage.setSafe();
									errorDeleteMessage.setType((short) 1);
									errorDeleteMessage.addGroup(clientsGroup);
									errorDeleteMessage.setData(new String("&delete&" + client + "&" + fileToDelete + "&" + master).getBytes());
									
									try {
										
										connection.multicast(errorDeleteMessage);
										
									} catch (SpreadException e) {
										
										e.printStackTrace();
										
									}
									
								}
								
							}
					
						}
						
					}
					
				}
				
				@Override
				public void membershipMessageReceived(SpreadMessage message) {

					displayMembershipMessage(message);
					
				}
				
			});
			
			// Show the menu.
			showMenu();
			
			// Get user command.
			while(true)
				getUserCommand();
			
		} else {
			
			System.err.println("Choose a server id between [0-1000].");
			
			System.exit(1);
			
		}
		
	}
	
	private void updateMaster(String instruction, String serverName, String priority) {
		
		if (instruction.equals("join")) {
			
			if (this.serverName.equals(serverName))
				System.out.println("New election due to the join of a new server.\n");
			
			this.votes = 0;
			
			this.tmServers.put(serverName, Integer.parseInt(priority));
			
			if (this.groupSize == this.tmServers.keySet().size()) {
				
				String tempMaster = this.serverName;
				int tempPriority = this.priority;
				
				for (String key : this.tmServers.keySet())
					if (tmServers.get(key) >= tempPriority) {
						
						tempMaster = key;
						tempPriority = this.tmServers.get(key);
						
					}
				
				SpreadMessage voteMessage = new SpreadMessage();
				voteMessage.setSafe();
				voteMessage.setType((short) 0);
				voteMessage.addGroup(this.group.toString());
				voteMessage.setData(new String("&vote&" + tempMaster + "&null").getBytes());
				
				try {
					
					this.connection.multicast(voteMessage);
					
				} catch (SpreadException e) {

					e.printStackTrace();
					
				}
				
			}
			
		}
			
		else if (instruction.equals("leave") || instruction.equals("disconnect")) {
			
			if (this.tmServers.containsKey(serverName)) {
				
				this.tmServers.remove(serverName);
		
				if (serverName.equals(this.master)) {
					
					if (instruction.equals("leave"))
						System.out.println("New election due to the leave of the Master.\n");
					
					else if (instruction.equals("disconnect"))
						System.out.println("New election due to the disconnect of the Master.\n");
					
					this.votes = 0;
					
					String tempMaster = this.serverName;
					int tempPriority = this.priority;
					
					for (String key : this.tmServers.keySet())
						if (tmServers.get(key) >= tempPriority) {
							
							tempMaster = key;
							tempPriority = this.tmServers.get(key);
							
						}
					
					SpreadMessage voteMessage = new SpreadMessage();
					voteMessage.setSafe();
					voteMessage.setType((short) 0);
					voteMessage.addGroup(this.group.toString());
					voteMessage.setData(new String("&vote&" + tempMaster + "&null").getBytes());
					
					try {
						
						this.connection.multicast(voteMessage);
						
					} catch (SpreadException e) {
	
						e.printStackTrace();
						
					}
					
				}
				
			}	
			
		}
		
		else if (instruction.equals("vote")) {
			
			if (this.serverName.equals(serverName)) {
				
				votes++;
				
				if (this.votes >= Math.floor( (groupSize * 1.0) / 2.0 ) + 1) {
					
					this.votes = 0;
					
					SpreadMessage setMasterMessage = new SpreadMessage();
					setMasterMessage.setSafe();
					setMasterMessage.setType((short) 0);
					setMasterMessage.addGroup(this.group.toString());
					setMasterMessage.setData(new String("&setmaster&" + this.serverName + "&null").getBytes());
					
					try {
						
						this.connection.multicast(setMasterMessage);
						
					} catch (SpreadException e) {

						e.printStackTrace();
						
					}
					
				}
				
			}
			
		}
		
		else if (instruction.equals("setmaster")) {
			
			this.master = serverName;
			
			System.out.println(this.serverName + ": " + this.master + " is the Master.\n");
			
		}
		
	}

	private void displayRegularMessage(SpreadMessage message) {

		try {
			
			System.out.println("\n**********LISTENER FOR REGULAR MESSAGES**********");
			
			if (message.isRegular()) {
				
				System.out.print("\nReceived a ");
				
				if (message.isUnreliable())
					System.out.print("UNRELIABLE");
				
				else if (message.isReliable())
					System.out.print("RELIABLE");
				
				else if (message.isFifo())
					System.out.print("FIFO");
				
				else if (message.isCausal())
					System.out.print("CAUSAL");
				
				else if(message.isAgreed())
					System.out.print("AGREED");
				
				else if(message.isSafe())
					System.out.print("SAFE");
				
				System.out.println(" message.");
				System.out.println("Sent by " + message.getSender());
				
				System.out.println("Type is " + message.getType() + ".");
				
				if(message.getEndianMismatch() == true)
					System.out.println("There is an endian mismatch.");
				else
					System.out.println("There is no endian mismatch.");

				SpreadGroup groups[] = message.getGroups();
				System.out.println("Sent to " + groups.length + " groups.");
				
				byte data[] = message.getData();
				System.out.println("The data has " + data.length + " bytes.");
				
				System.out.println("The message is: " + new String(data));
				
			}
			
		} catch(Exception e) {
			
			e.printStackTrace();
			
			System.exit(1);
			
		}
		
	}

	private void displayMembershipMessage(SpreadMessage message) {

		try {
			
			//System.out.println("\n**********LISTENER FOR MEMBERSHIP MESSAGES**********");
			
			if (message.isMembership()) {
				
				MembershipInfo info = message.getMembershipInfo();
				
				if (info.isRegularMembership()) {
					
					SpreadGroup members[] = info.getMembers();
					
					this.groupSize = members.length; 
					
					//MembershipInfo.VirtualSynchronySet virtual_synchrony_sets[] = info.getVirtualSynchronySets();
					//MembershipInfo.VirtualSynchronySet my_virtual_synchrony_set = info.getMyVirtualSynchronySet();

					//System.out.println("\nREGULAR membership for group " + group +
							   //" with " + members.length + " members:");
					
					//for (int i = 0; i < members.length; i++)
						//System.out.println("\t" + members[i]);

					//System.out.println("\nGroup ID is " + info.getGroupID());

					//System.out.print("\nDue to ");
					
					if (info.isCausedByJoin()) {
						
						//System.out.println("the JOIN of " + info.getJoined());
						System.out.println(info.getJoined().toString().split("#")[1] + " JOINED the group " + this.group.toString() + ".\n");
						
						SpreadMessage joinMessage = new SpreadMessage();
						joinMessage.setSafe();
						joinMessage.setType((short) 0);
						joinMessage.addGroup(info.getGroup().toString());
						joinMessage.setData(new String("&join&" + this.serverName + "&" + this.priority).getBytes());
						
						this.connection.multicast(joinMessage);
						
					}
					
					else if (info.isCausedByLeave()) {
						
						//System.out.println("the LEAVE of " + info.getLeft());
						System.out.println(info.getLeft().toString().split("#")[1] + " LEFT the group " + this.group.toString() + ".\n");

						SpreadMessage leaveMessage = new SpreadMessage();
						leaveMessage.setSafe();
						leaveMessage.setType((short) 0);
						leaveMessage.addGroup(info.getGroup().toString());
						leaveMessage.setData(new String("&leave&" + info.getLeft().toString().split("#")[1] + "&null").getBytes());

						this.connection.multicast(leaveMessage);
						
					}
					
					else if (info.isCausedByDisconnect()) {
						
						//System.out.println("the DISCONNECT of " + info.getDisconnected());
						System.out.println(info.getDisconnected().toString().split("#")[1] + " was DISCONNECTED from the group " + this.group.toString() + ".\n");

						SpreadMessage disconnectMessage = new SpreadMessage();
						disconnectMessage.setSafe();
						disconnectMessage.setType((short) 0);
						disconnectMessage.addGroup(info.getGroup().toString());
						disconnectMessage.setData(new String("&disconnect&" + info.getDisconnected().toString().split("#")[1] + "&null").getBytes());

						this.connection.multicast(disconnectMessage);
						
					}
					
					//else if (info.isCausedByNetwork()) {
						
						//System.out.println("NETWORK change");
						
						//for (int i = 0; i < virtual_synchrony_sets.length; i++ ) {
							
							//MembershipInfo.VirtualSynchronySet set = virtual_synchrony_sets[i];
							//SpreadGroup setMembers[] = set.getMembers();
							
							//System.out.print("\t\t");
							
							//if (set == my_virtual_synchrony_set)
								//System.out.print("(LOCAL) ");
							//else
								//System.out.print("(OTHER) ");
							
							//System.out.println("Virtual Synchrony Set " + i + " has " +
									    //set.getSize() + " members:");
							
							//for (int j = 0; j < set.getSize(); j++)
								//System.out.println("\t\t\t" + setMembers[j]);
							
						//}
						
					//}
					
				} //else if(info.isTransition())
					//System.out.println("\nTRANSITIONAL membership for group " + group + ".");
				
				//else if(info.isSelfLeave())
					//System.out.println("\nSELF-LEAVE message for group " + group + ".");
				
			}
				
		} catch(Exception e) {
			
			e.printStackTrace();
			
			System.exit(1);
			
		}
		
	}
	
	private void getUserCommand() {
		
		// Get the input.
		char command[] = new char[1024];
		int inputLength = 0;
		
		try {

			inputLength = this.inputKeyboard.read(command);
			
		} catch(IOException e) {
			
			e.printStackTrace();
			
			System.exit(1);
			
		}
		
		try {
			
			switch(command[0]) {
			
			//CHECK
			case 'c':
				
				System.out.println("This is " + this.serverName + ", member of the group " + this.group.toString() + ", having priority " + this.priority + ".\n");
				System.out.println("The current size of this group is " + this.groupSize + ".");
				System.out.println("The current Master of this group is " + this.master + ", having priority " + String.valueOf(this.tmServers.get(this.master)) + ".\n");
				
				System.out.println("ALL SERVERS:");
				for (String key : this.tmServers.keySet())
					System.out.println("(" + key + "; PRIORITY: " + String.valueOf(this.tmServers.get(key)) + ")");
					
				System.out.println();
				
				break;
				
			//QUIT
			case 'q':
				
				// Quit.
				System.exit(0);
				
				break;
				
			default:
				
				// Unknown command.
				System.err.println("Unknown command.");
				
				// Show the menu again.
				showMenu();
				
			}
			
		} catch(Exception e) {

			e.printStackTrace();
			
			System.exit(1);
			
		}
		
	}

	private void showMenu() {

		// Show menu.
		System.out.print("\n" +
						 "============\n" +
						 "Server Menu:\n" +
						 "============\n" +
						 "\n" +
						 "\tc -- check server status\n" +
						 "\tq -- quit\n\n");
		
	}

	public final static void main(String[] args) {
		
		// Default values.
		String address = "localhost";
		int port = 4803;
		String server = null;
		int priority = 0;
		String group = "servers";
		
		if (args.length == 4) {
			
			// Check the args.
			for (int i = 0; i < args.length; i++) {
				
				// Check for server.
				if ((args[i].compareTo("-s") == 0) && (args.length > (i + 1))) {
					
					// Set server.
					i++;
					server = args[i];
					
				} 
				
				// Check for priority.
				else if((args[i].compareTo("-r") == 0) && (args.length > (i + 1))) {
					
					// Set priority.
					i++;
					priority = Integer.parseInt(args[i]);
					
				}
				
				else {
					
					System.out.print("Usage: java Server\n" + 
							 "\t[-s <server name>] : unique server name\n" +
							 "\t[-r <priority>]    : the priority of the server\n");
					
					System.exit(0);
					
				}
				
			}
			
		} else {
			
			System.out.print("Usage: java Server\n" + 
					 "\t[-s <server name>] : unique server name\n" +
					 "\t[-r <priority>]    : the priority of the process\n");
			
			System.exit(0);
			
		}
		
		Server s = new Server(address, port, server, priority, group);
		
	}

}