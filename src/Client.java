import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import spread.AdvancedMessageListener;
import spread.MembershipInfo;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;

public class Client {
	
	// The Spread Connection.
	private SpreadConnection connection;
	
	// The keyboard input.
	private InputStreamReader inputKeyboard;

	// The group of the client.
	private SpreadGroup group;
	
	// Number of members inside the group.
	private int groupSize;
	
	// The name of the client.
	private String clientName;
	
	// The group name of the Master.
	private String masterGroup;
	
	// The group name of the Slaves.
	private String slavesGroup;
	
	// The server name of the Master.
	private String master;
	
	// The server name of the Slave.
	private String slave;
	
	private Sleep sleeper;
	
	public Client(String address, int port, String client, String group) {
		
		if (Integer.parseInt(client.split("_")[1]) >= 0 &&
				Integer.parseInt(client.split("_")[1]) <= 1000) {
			
			// Setup the keyboard input.
			this.inputKeyboard = new InputStreamReader(System.in);
			
			// Establish the spread connection.
			try {
				
				this.connection = new SpreadConnection();
				this.connection.connect(InetAddress.getByName(address), port, client, false, true);
				
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
			this.clientName = client;

			this.group = new SpreadGroup();
			try {
				
				this.group.join(this.connection, group);
				
			} catch (SpreadException e) {
				
				e.printStackTrace();
				
			}
			
			this.masterGroup = "servers";
			this.slavesGroup = "slaves";
			this.sleeper = new Sleep();
			
			// Add the listeners
			this.connection.add(new AdvancedMessageListener() {
				
				@Override
				public void regularMessageReceived(SpreadMessage message) {
					
					byte data[] = message.getData();
					String dataMessage = new String(data);
					String splittedMessage[];
					
					// Messages sent from slaves
					if (message.getType() == 0) {
						
						splittedMessage = dataMessage.split("&");
						String instruction = splittedMessage[1];
						String client = splittedMessage[2];
						String filename = splittedMessage[3];
						String slave = splittedMessage[4];
						
						if (clientName.equals(client)) {
							
							if (instruction.equals("create")) {
								
								System.out.println("\n" + slave + ": The file " + filename + " was successfully created.\n");
								
								sleeper.doNotify();
								
							}
						
							else if (instruction.equals("read")) {

								String strFileToRead = splittedMessage[5];
								File fileToRead = new File(strFileToRead);
								
								System.out.println("\n" + slave + ": The file " + filename + " was successfully read.\n");
								System.out.println("The content of this file is:");
								
								try {
									
									List<String> fileContent = Files.readLines(fileToRead, Charsets.UTF_8);

									for (String line : fileContent)
										System.out.println(line);
									
								} catch (IOException e) {
									
									e.printStackTrace();
									
								}
								
								char command[] = new char[1024];
								int inputLength = 0;
								SpreadMessage releaseMessage;
								
								do {
									
									System.out.println("\n** Type 'r' to release the file " + filename + " **");
									
									try {

										inputLength = inputKeyboard.read(command);
										
									} catch(IOException e) {
										
										e.printStackTrace();
										
										System.exit(1);
										
									}
										
									try {
										
										switch(command[0]) {
										
										//RELEASE
										case 'r':
											
											releaseMessage = new SpreadMessage();
											releaseMessage.setSafe();
											releaseMessage.setType((short) 2);
											releaseMessage.addGroup(slavesGroup);
											releaseMessage.setData(new String("&releaseread&" + clientName + "&" + filename + "&" + slave).getBytes());
											
											connection.multicast(releaseMessage);
											
											System.out.println("\nThe file " + filename + " was successfully released.\n");
											
											break;
											
										default:
											
											// Unknown command.
											System.err.println("Unknown command.");
											
										}
										
									} catch(Exception e) {

										e.printStackTrace();
										
										System.exit(1);
										
									}
									
								} while (command[0] != 'r');
								
								sleeper.doNotify();
								
							}
							
							else if (instruction.equals("write")) {
								
								String strFileToWrite = splittedMessage[5];
								File fileToWrite = new File(strFileToWrite);
								
								System.out.println("\n" + slave + ": The file " + filename + " was successfully opened.\n");
								System.out.println("Enter the text that you want to write into the file:");
								
								Scanner scanner = new Scanner(System.in);
								
								String content = "";
								content = scanner.nextLine();
								
								try {
									
									Files.append(content + "\n", fileToWrite, Charsets.UTF_8);
									
									System.out.println("\nThe text was successfully wrote into the file.");
									
								} catch (IOException e) {
									
									e.printStackTrace();
									
								}
								
								char command[] = new char[1024];
								int inputLength = 0;
								SpreadMessage releaseMessage;
								
								do {
									
									System.out.println("\n** Type 'r' to release the file " + filename + " **");
									
									try {

										inputLength = inputKeyboard.read(command);
										
									} catch(IOException e) {
										
										e.printStackTrace();
										
										System.exit(1);
										
									}
										
									try {
										
										switch(command[0]) {
										
										//RELEASE
										case 'r':
											
											releaseMessage = new SpreadMessage();
											releaseMessage.setSafe();
											releaseMessage.setType((short) 2);
											releaseMessage.addGroup(slavesGroup);
											releaseMessage.setData(new String("&releasewrite&" + clientName + "&" + filename + "&" + slave).getBytes());
											
											connection.multicast(releaseMessage);
											
											System.out.println("\nThe file " + filename + " was successfully released.\n");
											
											break;
											
										default:
											
											// Unknown command.
											System.err.println("Unknown command.");
											
										}
										
									} catch(Exception e) {

										e.printStackTrace();
										
										System.exit(1);
										
									}
									
								} while (command[0] != 'r');
								
								sleeper.doNotify();
							
							}
							
							else if (instruction.equals("delete")) {
							
								System.out.println("\n" + slave + ": The file " + filename + " was successfully deleted.\n");
								
								sleeper.doNotify();
								
							}
							
						}
							
					}
					
					// Error messages sent from Master
					else if (message.getType() == 1) {
						
						splittedMessage = dataMessage.split("&");
						String instruction = splittedMessage[1];
						String client = splittedMessage[2];
						String filename = splittedMessage[3];
						String master = splittedMessage[4];
						
						if (clientName.equals(client)) {
							
							if (instruction.equals("create")) {
								
								System.err.println("\n" + master + " (Master): The file " + filename + " already exists, you must delete it before trying to create it.\n");
								
								sleeper.doNotify();
								
							}
							
							else if (instruction.equals("read")) {
								
								System.err.println("\n" + master + " (Master): The file " + filename + " doesn't exist, you must create it before trying to read it.\n");
							
								sleeper.doNotify();
								
							}
							
							else if (instruction.equals("write")) {
								
								System.err.println("\n" + master + " (Master): The file " + filename + " doesn't exist, you must create it before trying to write in.\n");
								
								sleeper.doNotify();
								
							}
							
							else if (instruction.equals("delete")) {
								
								System.err.println("\n" + master + " (Master): The file " + filename + " doesn't exist, you must create it before trying to delete it.\n");
								
								sleeper.doNotify();
								
							}
								
						}
						
					}
					
					// Error messages sent from slaves
					else if (message.getType() == 2) {
						
						splittedMessage = dataMessage.split("&");
						String instruction = splittedMessage[1];
						String client = splittedMessage[2];
						String filename = splittedMessage[3];
						String slave = splittedMessage[4];
						
						if (clientName.equals(client)) {
							
							if (instruction.equals("create"))
								sleeper.doNotify();
							
							else if (instruction.equals("read")) {
								
								System.err.println("\n" + slave + ": You can't read the file " + filename + " right now, someone is current writing into this file. Try again in a while.\n");
								
								sleeper.doNotify();
								
							}
							
							else if (instruction.equals("write")) {
								
								String lock = splittedMessage[5];
								
								if (lock.equals("readlock"))
									System.err.println("\n" + slave + ": You can't write into the file " + filename + " right now, someone is current reading this file. Try again in a while.\n");
								
								else if (lock.equals("writelock"))
									System.err.println("\n" + slave + ": You can't write into the file " + filename + " right now, someone is current writing into this file. Try again in a while.\n");
								
								sleeper.doNotify();
								
							}
							
							else if (instruction.equals("delete")) {

								String lock = splittedMessage[5];
								
								if (lock.equals("readlock"))
									System.err.println("\n" + slave + ": You can't delete the file " + filename + " right now, someone is current reading this file. Try again in a while.\n");
								
								else if (lock.equals("writelock"))
									System.err.println("\n" + slave + ": You can't delete the file " + filename + " right now, someone is current writing into this file. Try again in a while.\n");
								
								sleeper.doNotify();
								
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
			
			// Get a user command.
			while(true)
				getUserCommand();
			
		} else {
			
			System.err.println("Choose a server id between [0-1000].");
			
			System.exit(1);
			
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
					
					MembershipInfo.VirtualSynchronySet virtual_synchrony_sets[] = info.getVirtualSynchronySets();
					MembershipInfo.VirtualSynchronySet my_virtual_synchrony_set = info.getMyVirtualSynchronySet();

					//System.out.println("\nREGULAR membership for group " + group +
							  // " with " + members.length + " members:");
					
					//for (int i = 0; i < members.length; i++)
						//System.out.println("\t" + members[i]);

					//System.out.println("\nGroup ID is " + info.getGroupID());

					//System.out.print("\nDue to ");
					
					if (info.isCausedByJoin()) {
						
						//System.out.println("the JOIN of " + info.getJoined());
						System.out.println(info.getJoined().toString().split("#")[1] + " JOINED the group " + this.group.toString() + ".\n");
						
//						SpreadMessage joinMessage = new SpreadMessage();
//						joinMessage.setSafe();
//						joinMessage.setType((short) 0);
//						joinMessage.addGroup(info.getGroup().toString());
//						joinMessage.setData(new String("&join&" + this.clientName).getBytes());
//						
//						this.connection.multicast(joinMessage);
						
					}
					
					else if (info.isCausedByLeave()) {
						
						//System.out.println("the LEAVE of " + info.getLeft());
						System.out.println(info.getLeft().toString().split("#")[1] + " LEFT the group " + this.group.toString() + ".\n");
						
//						SpreadMessage leaveMessage = new SpreadMessage();
//						leaveMessage.setSafe();
//						leaveMessage.setType((short) 0);
//						leaveMessage.addGroup(info.getGroup().toString());
//						leaveMessage.setData(new String("&leave&" + this.clientName).getBytes());
//						
//						this.connection.multicast(leaveMessage);
						
					}
					
					else if (info.isCausedByDisconnect()) {
						
						//System.out.println("the DISCONNECT of " + info.getDisconnected());
						System.out.println(info.getDisconnected().toString().split("#")[1] + " was DISCONNECTED from the group " + this.group.toString() + ".\n");
						
//						SpreadMessage disconnectMessage = new SpreadMessage();
//						disconnectMessage.setSafe();
//						disconnectMessage.setType((short) 0);
//						disconnectMessage.addGroup(info.getGroup().toString());
//						disconnectMessage.setData(new String("&disconnect&" + this.clientName).getBytes());
//						
//						this.connection.multicast(disconnectMessage);
					
					}
					
					else if (info.isCausedByNetwork()) {
						
						//System.out.println("NETWORK change");
						
						for (int i = 0; i < virtual_synchrony_sets.length; i++ ) {
							
							MembershipInfo.VirtualSynchronySet set = virtual_synchrony_sets[i];
							SpreadGroup setMembers[] = set.getMembers();
							
							//System.out.print("\t\t");
							
							//if (set == my_virtual_synchrony_set)
								//System.out.print("(LOCAL) ");
							//else
								//System.out.print("(OTHER) ");
							
							//System.out.println("Virtual Synchrony Set " + i + " has " +
									    //set.getSize() + " members:");
							
							//for (int j = 0; j < set.getSize(); j++)
								//System.out.println("\t\t\t" + setMembers[j]);
							
						}
						
					}
					
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

		// Show the prompt.
//		System.out.print("\n" + 
//						 "Server> ");
		
		// Get the input.
		char command[] = new char[1024];
		char filename[] = new char[1024];
		int inputLength = 0;
		SpreadMessage message;
		String groupMessage;
		
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
				
				System.out.println("This is " + this.clientName + ", member of the group " + this.group.toString() + ".\n");
				System.out.println("The current size of this group is " + this.groupSize + ".\n");
					
				break;
				
			//CREATE
			case 'n':
				
				// Get the filename.
				System.out.print("Enter the name of the file that you want to create:\n");
				
				inputLength = this.inputKeyboard.read(filename);
				
				groupMessage = "&create&" + this.clientName + "&" + new String(filename, 0, inputLength - 1);
				
				message = new SpreadMessage();
				message.setSafe();
				message.setType((short) 3);
				message.addGroup(this.masterGroup);
				message.setData(groupMessage.getBytes());
				
				this.connection.multicast(message);
				
				this.sleeper.doWait();

				break;
				
			//READ
			case 'r':
				
				// Get the filename.
				System.out.print("Enter the name of the file that you want to read:\n");
				
				inputLength = this.inputKeyboard.read(filename);
				
				groupMessage = "&read&" + this.clientName + "&" + new String(filename, 0, inputLength - 1);
				
				message = new SpreadMessage();
				message.setSafe();
				message.setType((short) 3);
				message.addGroup(this.masterGroup);
				message.setData(groupMessage.getBytes());
				
				this.connection.multicast(message);
				
				this.sleeper.doWait();

				break;
				
			//WRITE
			case 'w':
				
				// Get the filename.
				System.out.print("Enter the name of the file that you want to write in:\n");
				
				inputLength = this.inputKeyboard.read(filename);
				
				groupMessage = "&write&" + this.clientName + "&" + new String(filename, 0, inputLength - 1);
				
				message = new SpreadMessage();
				message.setSafe();
				message.setType((short) 3);
				message.addGroup(this.masterGroup);
				message.setData(groupMessage.getBytes());
				
				this.connection.multicast(message);
				
				this.sleeper.doWait();

				break;
				
			//DELETE
			case 'd':
				
				// Get the filename.
				System.out.print("Enter the name of the file that you want to delete:\n");
				
				inputLength = this.inputKeyboard.read(filename);
				
				groupMessage = "&delete&" + this.clientName + "&" + new String(filename, 0, inputLength - 1);
				
				message = new SpreadMessage();
				message.setSafe();
				message.setType((short) 3);
				message.addGroup(this.masterGroup);
				message.setData(groupMessage.getBytes());
				
				this.connection.multicast(message);
				
				this.sleeper.doWait();

				break;
				
			//QUIT
			case 'q':
				
				// Disconnect.
				//this.connection.disconnect();
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
						 "\tc -- check client status\n" +
						 "\n" +
						 "\tn -- create new file\n" +
						 "\tr -- read file\n" +
						 "\tw -- write into file\n" +
						 "\td -- delete file\n" +
						 "\n" +
						 "\tq -- quit\n\n");
		
	}

	public final static void main(String[] args) {
		
		// Default values.
		String address = "localhost";
		int port = 4803;
		String client = null;
		String group = "clients";
		
		if (args.length == 2) {
			
			// Check the args.
			for (int i = 0; i < args.length; i++) {
				
				// Check for user.
				if ((args[i].compareTo("-c") == 0) && (args.length > (i + 1))) {
					
					// Set user.
					i++;
					client = args[i];
					
				} 
				
				else {
					
					System.out.print("Usage: java Client\n" + 
							 "\t[-c <client name>] : unique client name\n");
					
					System.exit(0);
					
				}
				
			}
			
		} else {
			
			System.out.print("Usage: java Client\n" + 
					 "\t[-c <client name>] : unique client name\n");
			
			System.exit(0);
			
		}
		
		Client c = new Client(address, port, client, group);
		
	}

}