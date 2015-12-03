import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import spread.AdvancedMessageListener;
import spread.MembershipInfo;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;

public class Slave {
	
	private static final String FILE_PATH = "/home/andre/Desktop/File System/";
	
	// The Spread Connection.
	private SpreadConnection connection;
	
	// The keyboard input.
	private InputStreamReader inputKeyboard;

	// The group of the slave.
	private SpreadGroup group;
	
	// Number of members inside the group.
	private int groupSize;
	
	// The name of the slave.
	private String slaveName;
	
	// The group name of the Clients.
	private String clientsGroup;
	
	// The group name of the Master.
	private String masterGroup;
	
	// Structure to store the filenames of the slave
	private Set<String> tsFiles;
	
	// Which operation is being executed
	private String turn;
	
	// Structure to store the files and the clients that are reading those files
	private Map<String, ArrayList<String>> hmReadLock;
	
	// Structure to store the files and the clients that are writing in those files
	private Map<String, String> hmWriteLock;
	
	// Structure to store the reading requests from clients
	private Map<String, String> lhmWaitingRead;
	
	// Structure to store the writing requests from clients
	private Map<String, String> lhmWaitingWrite;
	
	public Slave(String address, int port, String slave, String group) {
		
		if (Integer.parseInt(slave.split("_")[1]) >= 0 &&
				Integer.parseInt(slave.split("_")[1]) <= 1000) {
			
			// Setup the keyboard input.
			this.inputKeyboard = new InputStreamReader(System.in);
			
			// Establish the spread connection.
			try {
				
				this.connection = new SpreadConnection();
				this.connection.connect(InetAddress.getByName(address), port, slave, false, true);
				
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
			this.slaveName = slave;

			this.group = new SpreadGroup();
			try {
				
				this.group.join(this.connection, group);
				
			} catch (SpreadException e) {
				
				e.printStackTrace();
				
			}
			
			this.masterGroup = "servers";
			this.clientsGroup = "clients";
			this.tsFiles = new TreeSet<String>();
			this.turn = "";
			this.hmReadLock = new HashMap<String, ArrayList<String>>();
			this.hmWriteLock = new HashMap<String, String>();
			this.lhmWaitingRead = new LinkedHashMap<String, String>();
			this.lhmWaitingWrite = new LinkedHashMap<String, String>();
			
			// Add the listeners
			this.connection.add(new AdvancedMessageListener() {
				
				@Override
				public void regularMessageReceived(SpreadMessage message) {

					byte data[] = message.getData();
					String dataMessage = new String(data);
					String splittedMessage[];
					SpreadMessage updatedMessage;
					
					// Message sent from Master to slave to resolve file operations
					if (message.getType() == 0) {
						
						splittedMessage = dataMessage.split("&");
						String slave = splittedMessage[4];
						
						if (slaveName.equals(slave)) {
							
							String instruction = splittedMessage[1];
							String client = splittedMessage[2];
							String filename = splittedMessage[3];

							if (instruction.equals("create")) {
								
								File fileToCreate = new File(FILE_PATH + slaveName + "/" + filename);
								try {
									
									fileToCreate.createNewFile();
									
									tsFiles.add(filename);
									
								} catch (IOException e1) {
									
									e1.printStackTrace();
									
								}
								
								SpreadMessage addFileMessage = new SpreadMessage();
								addFileMessage.setSafe();
								addFileMessage.setType((short) 2);
								addFileMessage.addGroup(masterGroup);
								addFileMessage.setData(new String("&addfile&" + slaveName + "&" + filename).getBytes());
								
								try {
									
									connection.multicast(addFileMessage);
									
								} catch (SpreadException e1) {
									
									e1.printStackTrace();
									
								}
								
								SpreadMessage createFileMessage = new SpreadMessage();
								createFileMessage.setSafe();
								createFileMessage.setType((short) 0);
								createFileMessage.addGroup(clientsGroup);
								createFileMessage.setData(new String("&create&" + client + "&" + filename + "&" + slave).getBytes());
								
								try {
									
									connection.multicast(createFileMessage);
									
								} catch (SpreadException e) {
									
									e.printStackTrace();
									
								}
								
							} else if (instruction.equals("read")) {
								
								// Write lock
								if (hmWriteLock.containsKey(filename)) {
									
									// Write lock
									if (!hmWriteLock.get(filename).equals("")) {
									
										SpreadMessage errorReadMessage = new SpreadMessage();
										errorReadMessage.setSafe();
										errorReadMessage.setType((short) 2);
										errorReadMessage.addGroup(clientsGroup);
										errorReadMessage.setData(new String("&read&" + client + "&" + filename + "&" + slave).getBytes());
										
										try {
											
											connection.multicast(errorReadMessage);
											
										} catch (SpreadException e1) {
											
											e1.printStackTrace();
											
										}
									
									}
									
									// Non write lock
									else {
										
										File fileToRead = new File(FILE_PATH + slaveName + "/" + filename);
										
										SpreadMessage readFileMessage = new SpreadMessage();
										readFileMessage.setSafe();
										readFileMessage.setType((short) 0);
										readFileMessage.addGroup(clientsGroup);
										readFileMessage.setData(new String("&read&" + client + "&" + filename + "&" + slave + "&" + fileToRead.getAbsolutePath()).getBytes());
										
										try {
											
											connection.multicast(readFileMessage);
											
											if (!hmReadLock.containsKey(filename))
												hmReadLock.put(filename, new ArrayList<String>());
											
											ArrayList<String> readers = hmReadLock.get(filename);
											readers.add(client);
											
											hmReadLock.put(filename, readers);
												
										} catch (SpreadException e1) {
											
											e1.printStackTrace();
											
										}
										
									}
									
								}
										
								// Non write lock
								else {
										
									File fileToRead = new File(FILE_PATH + slaveName + "/" + filename);
									
									SpreadMessage readFileMessage = new SpreadMessage();
									readFileMessage.setSafe();
									readFileMessage.setType((short) 0);
									readFileMessage.addGroup(clientsGroup);
									readFileMessage.setData(new String("&read&" + client + "&" + filename + "&" + slave + "&" + fileToRead.getAbsolutePath()).getBytes());
									
									try {
										
										connection.multicast(readFileMessage);
										
										if (!hmReadLock.containsKey(filename))
											hmReadLock.put(filename, new ArrayList<String>());
										
										ArrayList<String> readers = hmReadLock.get(filename);
										readers.add(client);
										
										hmReadLock.put(filename, readers);
											
									} catch (SpreadException e1) {
										
										e1.printStackTrace();
										
									}
										
								}
								
							} else if (instruction.equals("write")) {
								
								boolean readLock = false;
								boolean writeLock = false;
								
								// Read lock
								if (hmReadLock.containsKey(filename)) {
									
									if (hmReadLock.get(filename).size() > 0) {
									
										SpreadMessage errorWriteMessage = new SpreadMessage();
										errorWriteMessage.setSafe();
										errorWriteMessage.setType((short) 2);
										errorWriteMessage.addGroup(clientsGroup);
										errorWriteMessage.setData(new String("&write&" + client + "&" + filename + "&" + slave + "&readlock").getBytes());
										
										try {
											
											connection.multicast(errorWriteMessage);
											
										} catch (SpreadException e1) {
											
											e1.printStackTrace();
											
										}
										
										readLock = true;
										
									}
									
								}
								
								// Write lock
								if (hmWriteLock.containsKey(filename)) {
									
									if (!hmWriteLock.get(filename).equals("")) {
									
										SpreadMessage errorWriteMessage = new SpreadMessage();
										errorWriteMessage.setSafe();
										errorWriteMessage.setType((short) 2);
										errorWriteMessage.addGroup(clientsGroup);
										errorWriteMessage.setData(new String("&write&" + client + "&" + filename + "&" + slave + "&writelock").getBytes());
										
										try {
											
											connection.multicast(errorWriteMessage);
											
										} catch (SpreadException e1) {
											
											e1.printStackTrace();
											
										}
									
										writeLock = true;
										
									}
										
								}
									
								// Non read or write lock
								if (!readLock && !writeLock) {
									
									File fileToWrite = new File(FILE_PATH + slaveName + "/" + filename);
									
									SpreadMessage writeFileMessage = new SpreadMessage();
									writeFileMessage.setSafe();
									writeFileMessage.setType((short) 0);
									writeFileMessage.addGroup(clientsGroup);
									writeFileMessage.setData(new String("&write&" + client + "&" + filename + "&" + slave + "&" + fileToWrite.getAbsolutePath()).getBytes());
									
									try {
										
										connection.multicast(writeFileMessage);
										
										hmWriteLock.put(filename, client);
										
									} catch (SpreadException e1) {
										
										e1.printStackTrace();
										
									}
									
								}
								
							} else if (instruction.equals("delete")) {
								
								boolean readLock = false;
								boolean writeLock = false;
								
								// Read lock
								if (hmReadLock.containsKey(filename)) {
									
									if (hmReadLock.get(filename).size() > 0) {
									
										SpreadMessage errorWriteMessage = new SpreadMessage();
										errorWriteMessage.setSafe();
										errorWriteMessage.setType((short) 2);
										errorWriteMessage.addGroup(clientsGroup);
										errorWriteMessage.setData(new String("&delete&" + client + "&" + filename + "&" + slave + "&readlock").getBytes());
										
										try {
											
											connection.multicast(errorWriteMessage);
											
										} catch (SpreadException e1) {
											
											e1.printStackTrace();
											
										}
										
										readLock = true;
										
									}
									
								}
								
								// Write lock
								if (hmWriteLock.containsKey(filename)) {
									
									if (!hmWriteLock.get(filename).equals("")) {
									
										SpreadMessage errorWriteMessage = new SpreadMessage();
										errorWriteMessage.setSafe();
										errorWriteMessage.setType((short) 2);
										errorWriteMessage.addGroup(clientsGroup);
										errorWriteMessage.setData(new String("&delete&" + client + "&" + filename + "&" + slave + "&writelock").getBytes());
										
										try {
											
											connection.multicast(errorWriteMessage);
											
										} catch (SpreadException e1) {
											
											e1.printStackTrace();
											
										}
									
										writeLock = true;
										
									}
										
								}
									
								// Non read or write lock
								if (!readLock && !writeLock) {
									
									File fileToDelete = new File(FILE_PATH + slaveName + "/" + filename);
									fileToDelete.delete();
									
									tsFiles.remove(filename);
									
									SpreadMessage deleteFileMessage;
									
									deleteFileMessage = new SpreadMessage();
									deleteFileMessage.setSafe();
									deleteFileMessage.setType((short) 2);
									deleteFileMessage.addGroup(masterGroup);
									deleteFileMessage.setData(new String("&deletefile&" + slaveName + "&" + filename).getBytes());
									
									try {
										
										connection.multicast(deleteFileMessage);
										
									} catch (SpreadException e1) {
										
										e1.printStackTrace();
										
									}									
									
									deleteFileMessage = new SpreadMessage();
									deleteFileMessage.setSafe();
									deleteFileMessage.setType((short) 0);
									deleteFileMessage.addGroup(clientsGroup);
									deleteFileMessage.setData(new String("&delete&" + client + "&" + filename + "&" + slave + "&" + fileToDelete.getAbsolutePath()).getBytes());
									
									try {
										
										connection.multicast(deleteFileMessage);
										
										hmWriteLock.put(filename, client);
										
									} catch (SpreadException e1) {
										
										e1.printStackTrace();
										
									}
									
								}
								
							}
							
						}
						
					}
					
					// Message sent from Master to slave to resolve file consistency
					else if (message.getType() == 1) {
						
						splittedMessage = dataMessage.split("&");
						String slave = splittedMessage[2];
						
						if (slaveName.equals(slave)) {
							
							String instruction = splittedMessage[1];
							
							if (instruction.equals("deletefile"))
								if (splittedMessage.length == 4) {
									
									String files[] = splittedMessage[3].split("#");
									
									for (String file : files) {
										
										File fileToDelete = new File(FILE_PATH + slave + "/" + file);
										fileToDelete.delete();
										
										tsFiles.remove(file);
										
									}
									
								}
							
						}
						
					}
					
					// Message sent from clients to slave
					else if (message.getType() == 2) {
						
						splittedMessage = dataMessage.split("&");
						String slave = splittedMessage[4];
						
						if (slaveName.equals(slave)) {
							
							String instruction = splittedMessage[1];
							
							if (instruction.equals("releaseread")) {
								
								String client = splittedMessage[2];
								String fileToRelease = splittedMessage[3];
								
								ArrayList<String> readers = hmReadLock.get(fileToRelease);
								readers.remove(client);
								
								hmReadLock.put(fileToRelease, readers);
								
							} else if (instruction.equals("releasewrite")) {
								
								String fileToRelease = splittedMessage[3];
								
								hmWriteLock.put(fileToRelease, "");
								
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
						joinMessage.setType((short) 1);
						joinMessage.addGroup(this.masterGroup);
						joinMessage.setData(new String("&join&" + this.slaveName).getBytes());
						
						this.connection.multicast(joinMessage);
						
						File slaveFolder = new File(FILE_PATH + this.slaveName);
						
						if (!slaveFolder.exists())
							slaveFolder.mkdir();
						
						File listOfFiles[] = new File(FILE_PATH + this.slaveName).listFiles();
						
						String files = "";
						
						for (int i = 0; i < listOfFiles.length; i++) {
						
							this.tsFiles.add(listOfFiles[i].getName());
							
							if (i > 0)
								files += "#" + listOfFiles[i].getName();
							else
								files += listOfFiles[i].getName();
							
						}
						
						SpreadMessage filesMessage = new SpreadMessage();
						filesMessage.setSafe();
						filesMessage.setType((short) 2);
						filesMessage.addGroup(this.masterGroup);
						filesMessage.setData(new String("&loadfiles&" + this.slaveName + "&" + files).getBytes());
						
						this.connection.multicast(filesMessage);
						
					}
					
					else if (info.isCausedByLeave()) {
						
						//System.out.println("the LEAVE of " + info.getLeft());
						System.out.println(info.getLeft().toString().split("#")[1] + " LEFT the group " + this.group.toString() + ".\n");

						SpreadMessage leaveMessage = new SpreadMessage();
						leaveMessage.setSafe();
						leaveMessage.setType((short) 1);
						leaveMessage.addGroup(this.masterGroup);
						leaveMessage.setData(new String("&leave&" + info.getLeft().toString().split("#")[1]).getBytes());
						
						this.connection.multicast(leaveMessage);
						
					}
					
					else if (info.isCausedByDisconnect()) {
						
						//System.out.println("the DISCONNECT of " + info.getDisconnected());
						System.out.println(info.getDisconnected().toString().split("#")[1] + " was DISCONNECTED from the group " + this.group.toString() + ".\n");

						SpreadMessage disconnectMessage = new SpreadMessage();
						disconnectMessage.setSafe();
						disconnectMessage.setType((short) 1);
						disconnectMessage.addGroup(this.masterGroup);
						disconnectMessage.setData(new String("&disconnect&" + info.getDisconnected().toString().split("#")[1]).getBytes());
						
						this.connection.multicast(disconnectMessage);
					
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
		int inputLength = 0;
		
		try {

			inputLength = this.inputKeyboard.read(command);
			
		} catch(IOException e) {
			
			e.printStackTrace();
			
			System.exit(1);
			
		}
		
		// Setup a tokenizer for the input.
		StreamTokenizer tokenizer = new StreamTokenizer(new StringReader(new String(command, 1, inputLength - 1)));
		
		// Check what it is.
		SpreadMessage message;
		char buffer[];
		
		try {
			
			switch(command[0]) {
			
			//CHECK
			case 'c':
				
				System.out.println("This is " + this.slaveName + ", member of the group " + this.group.toString() + "\n");
				System.out.println("The current size of this group is " + this.groupSize + "\n");
					
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
						 "\tq -- quit\n\n");
		
	}

	public final static void main(String[] args) {
		
		// Default values.
		String address = "localhost";
		int port = 4803;
		String slave = null;
		String group = "slaves";
		
		if (args.length == 2) {
			
			// Check the args.
			for (int i = 0; i < args.length; i++) {
				
				// Check for user.
				if ((args[i].compareTo("-s") == 0) && (args.length > (i + 1))) {
					
					// Set user.
					i++;
					slave = args[i];
					
				} 
				
				else {
					
					System.out.print("Usage: java Slave\n" + 
							 "\t[-s <slave name>] : unique slave name\n");
					
					System.exit(0);
					
				}
				
			}
			
		} else {
			
			System.out.print("Usage: java Slave\n" + 
					 "\t[-s <slave name>] : unique slave name\n");
			
			System.exit(0);
			
		}
		
		Slave s = new Slave(address, port, slave, group);
		
	}

}