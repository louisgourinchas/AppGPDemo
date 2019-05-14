import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.zsmartsystems.zigbee.transport.ZigBeePort.FlowControl;

public class ApplicationMain {
    
	private static String deviceType;
	
	public static void main(String[] args) {	
		final int ZEP_UDP_PORT = 17754;

	    final String serialPortName;
	    final Integer serialBaud = 57600;
	    FlowControl flowControl = FlowControl.FLOWCONTROL_OUT_XONOFF;
	    
	    
		System.out.println("Starting. . .");
		
		Options options = new Options();
		
		options.addOption(Option.builder("d").longOpt("device").argName("device type").hasArg().desc("Specifies the device being tested")
                .required().build());
		options.addOption(Option.builder("p").longOpt("port").argName("port name").hasArg().desc("Set the port")
                .required().build());
		
		CommandLine cmdline;
        try {
            CommandLineParser parser = new DefaultParser();
            cmdline = parser.parse(options, args);

            if (!cmdline.hasOption("port")) {
                System.err.println("Serial port must be specified with the 'port' option");
                return;
            }
            if (!cmdline.hasOption("device")) {
            	System.err.println("Device type must be specified with the 'device' option");
            	return;
            }
            
            switch(cmdline.getOptionValue("device").toLowerCase()) {
    		case "onoff":
    			deviceType="onoff";
    			break;
    		
    		case "sensor":
    			deviceType="sensor";
    			break;
    			
    		default:
    			System.err.println("Unknown device type specified: " + cmdline.getOptionValue("device").toLowerCase());	
    			return;
            }
            
            serialPortName = cmdline.getOptionValue("port");
            
        } catch (org.apache.commons.cli.ParseException exp) {
            System.err.println("Parsing command line failed.  Reason: " + exp.getMessage());
            return;
        }
        
        System.out.println("Everything's fine boy. Device type: " + deviceType + ". Selected port: " + serialPortName);
        
	}

}
