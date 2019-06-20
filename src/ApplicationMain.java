import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.xml.DOMConfigurator;

import com.zsmartsystems.zigbee.ExtendedPanId;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeChannel;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeStatus;
import com.zsmartsystems.zigbee.console.main.ZigBeeDataStore;
import com.zsmartsystems.zigbee.database.ZigBeeNetworkDataStore;
import com.zsmartsystems.zigbee.dongle.cc2531.zigbee.util.IEEEAddress;
import com.zsmartsystems.zigbee.dongle.ember.EmberNcp;
import com.zsmartsystems.zigbee.dongle.ember.ZigBeeDongleEzsp;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.EzspFrame;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpepIncomingMessageHandler;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGetCurrentSecurityStateRequest;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGetCurrentSecurityStateResponse;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpSinkTableFindOrAllocateEntryRequest;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpSinkTableFindOrAllocateEntryResponse;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpSinkTableGetEntryRequest;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpSinkTableGetEntryResponse;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpSinkTableInitRequest;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpSinkTableInitResponse;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpSinkTableLookupRequest;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpSinkTableLookupResponse;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpSinkTableSetEntryRequest;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.command.EzspGpSinkTableSetEntryResponse;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberGpAddress;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberGpApplicationId;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberGpSinkListEntry;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberGpSinkTableEntry;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberGpSinkTableEntryStatus;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberKeyData;
import com.zsmartsystems.zigbee.dongle.ember.greenpower.EzspGpSinkTable;
import com.zsmartsystems.zigbee.dongle.ember.greenpower.EzspGpSinkTableEntry;
import com.zsmartsystems.zigbee.dongle.ember.internal.transaction.EzspSingleResponseTransaction;
import com.zsmartsystems.zigbee.dongle.ember.internal.transaction.EzspTransaction;
import com.zsmartsystems.zigbee.greenpower.GpCommand;
import com.zsmartsystems.zigbee.greenpower.ZigBeeGpCommandListener;
import com.zsmartsystems.zigbee.security.ZigBeeKey;
import com.zsmartsystems.zigbee.serial.ZigBeeSerialPort;
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer;
import com.zsmartsystems.zigbee.serialization.DefaultSerializer;
import com.zsmartsystems.zigbee.transport.ConcentratorConfig;
import com.zsmartsystems.zigbee.transport.ConcentratorType;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.TransportConfigOption;
import com.zsmartsystems.zigbee.transport.ZigBeePort;
import com.zsmartsystems.zigbee.transport.ZigBeePort.FlowControl;

public class ApplicationMain {

	static Integer channelId;
	static EmberNcp emberNcp;
	static ZigBeeDongleEzsp dongle;

	public static void main(String[] args) {	
		//DOMConfigurator.configure("./log4j.xml");

		final String serialPortName;
		final Integer serialBaud = 57600;
		FlowControl flowControl = FlowControl.FLOWCONTROL_OUT_XONOFF;

		final TransportConfig transportOptions = new TransportConfig();

		System.out.println("Starting. . .");

		Options options = new Options();

		options.addOption(Option.builder("p").longOpt("port").argName("port name").hasArg().desc("Set the port")
				.required().build());
		options.addOption(Option.builder("c").longOpt("channel").argName("channel id").hasArg().desc("Set the channel ID").build());

		CommandLine cmdline;
		try {
			CommandLineParser parser = new DefaultParser();
			cmdline = parser.parse(options, args);

			if (!cmdline.hasOption("port")) {
				System.err.println("Serial port must be specified with the 'port' option");
				return;
			}

			serialPortName = cmdline.getOptionValue("port");

		} catch (org.apache.commons.cli.ParseException exp) {
			System.err.println("Parsing command line failed.  Reason: " + exp.getMessage());
			return;
		}

		if (cmdline.hasOption("channel")) {
			channelId = Integer.parseInt(cmdline.getOptionValue("channel"));
		} else {
			channelId = 11;
		}

		final ZigBeePort serialPort = new ZigBeeSerialPort(serialPortName, serialBaud, flowControl);
		
		dongle = new ZigBeeDongleEzsp(serialPort);
//		dongle = new ZigBeeDongleEzsp(serialPort) {
//			
//			@Override
//			public void handlePacket(EzspFrame response) {
//				if (response instanceof EzspGpepIncomingMessageHandler) {
//					EzspGpepIncomingMessageHandler frame = (EzspGpepIncomingMessageHandler) response;
//					int sourceId = frame.getAddr().getSourceId();
//					
//					if (frame.getGpdCommandId()==224) {	
//						System.out.println("Frame Gp Commissionning reçue:");
//						System.out.println(response.toString());
//						System.out.println("Dongle ezsp transaction Starting");
//
//						EzspGpSinkTableInitRequest initTableTransactionRequest = new EzspGpSinkTableInitRequest();
//						
//						EzspGpSinkTableFindOrAllocateEntryRequest findIndexTransactionRequest = new EzspGpSinkTableFindOrAllocateEntryRequest();
//						findIndexTransactionRequest.setAddr(frame.getAddr());
//						
//						EzspGpSinkTableSetEntryRequest setEntryTransactionRequest = new EzspGpSinkTableSetEntryRequest();
//						setEntryTransactionRequest.setEntry(buildSinkTableEntry(frame));
//						
//						EzspGpSinkTableGetEntryRequest getEntryTransactionRequest = new EzspGpSinkTableGetEntryRequest();
//						
//						EzspGetCurrentSecurityStateRequest getSecurityState = new EzspGetCurrentSecurityStateRequest();
//						
//						getExecutorService().execute(new Runnable() {
//							@Override
//							public void run() {
//								System.out.println("Sending frame...");
//								System.out.println(initTableTransactionRequest.toString());
//								
//								EzspTransaction transaction = getFrameHandler().sendEzspTransaction(new EzspSingleResponseTransaction(getSecurityState, EzspGetCurrentSecurityStateResponse.class));
//								EzspGetCurrentSecurityStateResponse getSecurityStateResponse = (EzspGetCurrentSecurityStateResponse) transaction.getResponse();
//								
//								System.out.println("Frame received: " + getSecurityStateResponse.toString());
//								System.out.println("Done.");
//							}
//						});
//						
//						getExecutorService().execute(new Runnable() {
//							@Override
//							public void run() {
//								System.out.println("Sending frame...");
//								System.out.println(initTableTransactionRequest.toString());
//								
//								EzspTransaction transaction = getFrameHandler().sendEzspTransaction(new EzspSingleResponseTransaction(initTableTransactionRequest, EzspGpSinkTableInitResponse.class));
//								EzspGpSinkTableInitResponse initTableTransactionResponse = (EzspGpSinkTableInitResponse) transaction.getResponse();
//								
//								System.out.println("Frame received: " + initTableTransactionResponse.toString());
//								System.out.println("Done.");
//							}
//						});
//						
//						getExecutorService().execute(new Runnable() {
//				            @Override
//				            public void run() {
//				            	System.out.println("Sending Frame. . .");
//				            	System.out.println(findIndexTransactionRequest.toString());
//				            	
//				            	EzspTransaction transaction = getFrameHandler().sendEzspTransaction(new EzspSingleResponseTransaction(findIndexTransactionRequest, EzspGpSinkTableFindOrAllocateEntryResponse.class));								
//				            	EzspGpSinkTableFindOrAllocateEntryResponse findIndexTransactionResponse = (EzspGpSinkTableFindOrAllocateEntryResponse) transaction.getResponse();
//								setEntryTransactionRequest.setSinkIndex(findIndexTransactionResponse.getIndex());
//								getEntryTransactionRequest.setSinkIndex(findIndexTransactionResponse.getIndex());
//								
//								System.out.println("Frame received: " + findIndexTransactionResponse.toString());
//				            }
//				        });				
//						
//						getExecutorService().execute(new Runnable() {
//				            @Override
//				            public void run() {
//				            	System.out.println("Sending Frame. . .");
//				            	System.out.println(setEntryTransactionRequest.toString());
//				            	
//				            	EzspTransaction transaction = getFrameHandler().sendEzspTransaction(new EzspSingleResponseTransaction(setEntryTransactionRequest, EzspGpSinkTableSetEntryResponse.class));			
//				            	EzspGpSinkTableSetEntryResponse setEntryTransactionResponse = (EzspGpSinkTableSetEntryResponse) transaction.getResponse();
//				            	
//								System.out.println("Frame received: " + setEntryTransactionResponse.toString());
//				            }
//				        });
//						
//						getExecutorService().execute(new Runnable() {
//				            @Override
//				            public void run() {
//				            	System.out.println("Sending Frame. . .");
//				            	System.out.println(getEntryTransactionRequest.toString());
//				            	
//				            	EzspTransaction transaction = getFrameHandler().sendEzspTransaction(new EzspSingleResponseTransaction(getEntryTransactionRequest, EzspGpSinkTableGetEntryResponse.class));			
//				            	EzspGpSinkTableGetEntryResponse getEntryTransactionResponse = (EzspGpSinkTableGetEntryResponse) transaction.getResponse();
//				            	
//								System.out.println("Frame received: " + getEntryTransactionResponse.toString());
//								System.out.println("Dongle ezsp transaction finished");
//				            }
//				        });				        						
//						
//					}
//					
//					if (frame.getGpdCommandId()==32 /*&& (sourceId==720976 || sourceId==786512)*/) {						
//						verifyDevicePairing(frame);								
//					}
//					
//					if (frame.getGpdCommandId()==33 /*&& (sourceId==720976 || sourceId==786512)*/) {						
//						verifyDevicePairing(frame);
//					}
//				}
//			}
//
//			private void verifyDevicePairing(EzspGpepIncomingMessageHandler frame) {
//				EzspGpSinkTableLookupRequest sinkTableLookupTransactionRequest = new EzspGpSinkTableLookupRequest();
//				sinkTableLookupTransactionRequest.setAddr(frame.getAddr());
//				
//				System.out.println("On/Off verification transaction started");
//				
//				getExecutorService().execute(new Runnable() {
//				    @Override
//				    public void run() {
//				    	System.out.println("Sending Frame. . .");
//				    	System.out.println(sinkTableLookupTransactionRequest.toString());
//				    	
//				    	EzspTransaction transaction = getFrameHandler().sendEzspTransaction(new EzspSingleResponseTransaction(sinkTableLookupTransactionRequest, EzspGpSinkTableLookupResponse.class));								
//				    	EzspGpSinkTableLookupResponse sinkTableLookupTransactionResponse = (EzspGpSinkTableLookupResponse) transaction.getResponse();
//						
//						System.out.println("Frame received: " + sinkTableLookupTransactionResponse.toString());
//				    }
//				});
//			}
//		};
		
		transportOptions.addOption(TransportConfigOption.RADIO_TX_POWER, 8);

		// Configure the concentrator
		// Max Hops defaults to system max
		ConcentratorConfig concentratorConfig = new ConcentratorConfig();
		concentratorConfig.setType(ConcentratorType.LOW_RAM);
		concentratorConfig.setMaxFailures(8);
		concentratorConfig.setMaxHops(0);
		concentratorConfig.setRefreshMinimum(60);
		concentratorConfig.setRefreshMaximum(3600);
		transportOptions.addOption(TransportConfigOption.CONCENTRATOR_CONFIG, concentratorConfig);

		ZigBeeNetworkManager networkManager = new ZigBeeNetworkManager(dongle);
		
		//TODO create a method to initialize GP functionnality as a whole.
		
		networkManager.setGpTransport(dongle);
		networkManager.setGpTransactionManager();
		networkManager.addGpCommandListener(new ZigBeeGpCommandListener() {			
			@Override
			public void gpCommandReceived(GpCommand command) {
				switch (command.getCommandId()) {
					case 32: //off
						System.out.println("[GP]Off command received");
						break;
					case 33: //on
						System.out.println("[GP]On command received");
						break;
					case 160: //attribute reporting
						System.out.println("[GP]Attribute reporting command received");
						int[] payload = command.getPayload();
						int type = (payload[1]  <<8) + payload[0];
						String str_type = "";
						switch(type) {
						case 1026:
							str_type="Mesure de la température";
							break;
						case 1029:
							str_type="Mesure de l'humidité";
							break;
						default:
							str_type="Unknown";
							break;
						}
						System.out.println("" + str_type + " reçue");
						float value = (payload[6]  <<8) + payload[5];
						String complement = "";
						if (type == 1026)
							complement = " °C";
						else
							complement=" %";
						System.out.println("Valeur reçue: " + value/100 + complement);
						break;
					case 224: //commissioning
					default:
						System.out.println("[Gp]Command received");
						break;
				}
				//System.out.println(command.toString());
			}
		});
		
		EzspGpSinkTable table = new EzspGpSinkTable();
		table.init();
		
		EmberGpAddress address = new EmberGpAddress();
		address.setGpdIeeeAddress(new IeeeAddress("BBCCDDAABBCCDD00"));
		address.setSourceId(14535867);
		address.setApplicationId(EmberGpApplicationId.getEmberGpApplicationId(0x1000));
		address.setEndpoint(179);
		
		int index = table.findOrAllocateEntry(address);
		EzspGpSinkTableEntry entry = new EzspGpSinkTableEntry();
		entry.setAddress(address);
		table.setEntry(index, entry);
		
		networkManager.useVirtualSink(table);
		
		ZigBeeNetworkDataStore dataStore = new ZigBeeDataStore("EMBER");
		networkManager.setNetworkDataStore(dataStore);
		networkManager.setSerializer(DefaultSerializer.class, DefaultDeserializer.class);
		
		ZigBeeStatus initResponse = networkManager.initialize();
		System.out.println("networkManager.initialize returned " + initResponse);

		System.out.println("PAN ID  I       = " + networkManager.getZigBeePanId());
		System.out.println("Extended PAN ID = " + networkManager.getZigBeeExtendedPanId());
		System.out.println("Channel         = " + networkManager.getZigBeeChannel());

		resetNetwork(networkManager);

		// Add the default ZigBeeAlliance09 HA link key
		transportOptions.addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, new ZigBeeKey(new int[] { 0x5A, 0x69,
				0x67, 0x42, 0x65, 0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39 }));
		// transportOptions.addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, new ZigBeeKey(new int[] { 0x41, 0x61,
		// 0x8F, 0xC0, 0xC8, 0x3B, 0x0E, 0x14, 0xA5, 0x89, 0x95, 0x4B, 0x16, 0xE3, 0x14, 0x66 }));

		dongle.updateTransportConfig(transportOptions);

		if (networkManager.startup(true) != ZigBeeStatus.SUCCESS) {
			System.out.println("Network Initialisation failed.");
		} else {
			System.out.println("Network initialization & setup succeeded.");
		}

	}

	//	private static boolean initializeOnOffDongle(String serialPortName, int serialBaud, FlowControl flowControl) {
	//		final ZigBeePort serialPort = new ZigBeeSerialPort(serialPortName, serialBaud, flowControl);
	//        System.out.println("Opened serial port " + serialPortName + " at " + serialBaud);
	//        dongle = new ZigBeeDongleEzsp(serialPort);
	//        
	//        dongle.initialize();
	//        
	//        String ncpVersion = dongle.getFirmwareVersion();
	//        if (ncpVersion.equals("")) {
	//            System.err.println("Unable to communicate with Ember NCP");
	//            shutdown();
	//            return false;
	//        }
	//        System.out.println("Ember NCP version     : " + ncpVersion);
	//
	//        emberNcp = dongle.getEmberNcp();
	//        IeeeAddress localIeeeAddress = emberNcp.getIeeeAddress();
	//        System.out.println("Ember NCP EUI         : " + localIeeeAddress);
	//        
	//        dongle.startup(true);
	//        
	//		return true;
	//	}

	private static void resetNetwork(ZigBeeNetworkManager networkManager) {
		ZigBeeKey nwkKey;
		ZigBeeKey linkKey;
		ExtendedPanId extendedPan;
		int pan;

		pan = parseDecimalOrHexInt("0x2000");
		extendedPan = new ExtendedPanId("987654321");
		nwkKey = new ZigBeeKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

		linkKey = new ZigBeeKey(new int[] { 0x5A, 0x69, 0x67, 0x42, 0x65, 0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61,
				0x6E, 0x63, 0x65, 0x30, 0x39 });

		System.out.println("*** Resetting network");
		System.out.println("  * Channel                = " + channelId);
		System.out.println("  * PAN ID                 = " + pan);
		System.out.println("  * Extended PAN ID        = " + extendedPan);
		System.out.println("  * Link Key               = " + linkKey);
		if (nwkKey.hasOutgoingFrameCounter()) {
			System.out.println("  * Link Key Frame Cnt     = " + linkKey.getOutgoingFrameCounter());
		}
		System.out.println("  * Network Key            = " + nwkKey);
		if (nwkKey.hasOutgoingFrameCounter()) {
			System.out.println("  * Network Key Frame Cnt  = " + nwkKey.getOutgoingFrameCounter());
		}

		networkManager.setZigBeeChannel(ZigBeeChannel.create(channelId));
		networkManager.setZigBeePanId(pan);
		networkManager.setZigBeeExtendedPanId(extendedPan);
		networkManager.setZigBeeNetworkKey(nwkKey);
		networkManager.setZigBeeLinkKey(linkKey);
	}

	protected static void packetReceived(int i, int lqi, int rssi, int[] data) {
		System.out.println("Frame received: \nLink Quality   : " + lqi + "\nrssi           : " + rssi + "\ndata           : " + getDataString(data));
	}

	private static String getDataString(int[] data) {
		String strres="";
		for(int i=0 ; i<data.length ; i++) {
			strres+=data[i]+" ";
		}
		return strres;
	}

	private static int parseDecimalOrHexInt(String strVal) {
		int radix = 10;
		String number = strVal;
		if (number.startsWith("0x")) {
			number = number.substring(2);
			radix = 16;
		}
		return Integer.parseInt(number, radix);
	}


	private static EmberGpSinkTableEntry buildSinkTableEntry(EzspGpepIncomingMessageHandler frame) {
		EmberGpSinkTableEntry entry = new EmberGpSinkTableEntry();
		int[] payload = frame.getGpdCommandPayload();
		
		//Status of the sink entry
		entry.setStatus(EmberGpSinkTableEntryStatus.EMBER_GP_SINK_TABLE_ENTRY_STATUS_ACTIVE);
		//options + extendedOptions
		//bits 0/1/2 --> application ID --> 000 = utilisation du sourceID 
		//bits3/4 --> communication mode --> 01 groupcast to DGroupID / 10 groupcast forwarding to pre commisionned groupID
		//bit 5 --> sequence number capabilities --> 1 pour increasing number
		//bit 6 --> RxOnCapability -−> 0 
		//bit 7 --> FixedLocation --> 1
		//bit 8 --> assignedalias --> 0
		//bit 9 --> security use --> 1
		//bits 10-15 reserved
		//valeur 0b 0000011101|000000 --> 3904 + ExtendedOptions full à 0 ?--> 30408704/486539264
		//000000 1 0 1 0 1 10 000 --> 02B0 --> -1342046208
		//000000 1 0 1 0 1 01 000 --> 02A8 --> -1476263936
		
		//0/1/2 --> app ID 000
		//3 --> entryactive 1
		//4 --> entryvalid 1
		//5 --> seq num capabilities 1
		//6 --> lightweight unicast GPS 0 (no lightweight sink address list parameter)
		//7 --> Derived group GPS 1
		//8 --> commissionned group GPS 0
		//9 --> FirstToForward 0
		//10 --> InRange 0
		//11 --> GPDFixed 0
		//12 --> HasAllUnicastRoutes 0
		//13 --> AssignedAlias 0
		//14 --> SecurityUse 1
		//15 --> ExtendedOptions 1
		// 1100000010111000 --> -1195376640
		entry.setOptions(-1476263936);

		//address of the GPD (the on/off) -> source address of the GpepIncMsg
		entry.setGpd(frame.getAddr());
		//the GPD's device ID, first field of the command payload.
		entry.setDeviceId(payload[0]);
		
		EmberGpSinkListEntry[] sinkList= { new EmberGpSinkListEntry() , new EmberGpSinkListEntry() };
		sinkList[0].setType(255);
		sinkList[1].setType(255);
		
		sinkList[0].setSinkEui(dongle.getIeeeAddress());
		sinkList[1].setSinkEui(dongle.getIeeeAddress());
		
		sinkList[0].setSinkNodeId(0);
		sinkList[1].setSinkNodeId(0);
		
		entry.setSinkList(sinkList);
		//the GPDs assigned alias (aka it's node type) 3 for EMBER_END_DEVICE
		entry.setAssignedAlias(3);
		//Group cast radius -> uint8_t --> 0 pour unspecified
		entry.setGroupcastRadius(0);
		//Security Options field -> uint8_t
		String security = "000" + Integer.toBinaryString(payload[2]).substring(3, 6) + Integer.toBinaryString(payload[2]).substring(6);
		entry.setSecurityOptions(Integer.parseInt(security, 2));
		//Gpd Security Frame Counter --> 24/25/26/27 du payload
		entry.setGpdSecurityFrameCounter((payload[26] <<24) + (payload[25] <<16) + (payload[24] <<8) + (payload[23]));
		
		EmberKeyData key = new EmberKeyData();
		key.setContents(Arrays.copyOfRange(payload, 3, 19));
		entry.setGpdKey(key);
		
		return entry;
	}

}
