import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import com.zsmartsystems.zigbee.ExtendedPanId;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeChannel;
import com.zsmartsystems.zigbee.ZigBeeNetworkManager;
import com.zsmartsystems.zigbee.ZigBeeStatus;
import com.zsmartsystems.zigbee.console.main.ZigBeeDataStore;
import com.zsmartsystems.zigbee.database.ZigBeeNetworkDataStore;
import com.zsmartsystems.zigbee.dongle.ember.EmberNcp;
import com.zsmartsystems.zigbee.dongle.ember.ZigBeeDongleEzsp;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberGpAddress;
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EmberGpApplicationId;
import com.zsmartsystems.zigbee.dongle.ember.greenpower.EzspGpSinkTable;
import com.zsmartsystems.zigbee.dongle.ember.greenpower.EzspGpSinkTableEntry;
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
		
		//TODO create a method to initialize GP functionality as a whole.
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
							str_type="Mesure de la temp�rature";
							break;
						case 1029:
							str_type="Mesure de l'humidit�";
							break;
						default:
							str_type="Unknown";
							break;
						}
						System.out.print("" + str_type + " re�ue : ");
						float value = (payload[6]  <<8) + payload[5];
						String complement = "";
						if (type == 1026)
							complement = " �C";
						else
							complement=" %";
						System.out.println(value/100 + complement);
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
		address.setGpdIeeeAddress(new IeeeAddress("FFFFFFAAFFFFFFAA"));
		address.setSourceId(-1426063361);
		address.setApplicationId(EmberGpApplicationId.EMBER_GP_APPLICATION_SOURCE_ID);
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
}
