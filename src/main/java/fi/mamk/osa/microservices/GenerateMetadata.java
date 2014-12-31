package fi.mamk.osa.microservices;

import java.util.HashMap;

import net.xeoh.plugins.base.annotations.Capabilities;
import net.xeoh.plugins.base.annotations.PluginImplementation;

import com.belvain.soswe.workflow.Microservice;

@PluginImplementation
public class GenerateMetadata extends Microservice {

    @Capabilities
    public String[] caps() {
        return new String[] {"name:GenerateMetadata"};
    }
    
    @Override
    public boolean execute(String input, HashMap<String, Object> options)
            throws Exception {
        
        boolean success = false;
        String output = "";
        String filename = "";
        String organization = "";
        String user = "";
        
        if (options != null) {
            if (options.containsKey("filename")) {
                filename = options.get("filename").toString();
            }
            if (options.containsKey("organization")) {
                organization = options.get("organization").toString();
            }
            if (options.containsKey("username")) {
                user = options.get("username").toString();
            }
        }
        
        if (input != null && !input.isEmpty()) {
            //Handle input from previous microservice here
        }
        
        output += "Metadata generated for "+filename+"\n";

        success = true;
        super.setState("completed");
        super.setOutput(output);
        super.setCompleted(true);
        
        String log = super.getLog().replace("{organization}", organization).replace("{user}", user);
        super.setLog(log);
        log();
        
        return success;
    }
    
}
