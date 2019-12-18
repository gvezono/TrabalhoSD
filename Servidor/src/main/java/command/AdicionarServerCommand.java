package command;

import io.atomix.copycat.Command;
import servidor.AddSvReply;
import servidor.AddSvRequest;

public class AdicionarServerCommand implements Command<AddSvReply> {

    public AddSvRequest req;

    public AdicionarServerCommand(AddSvRequest req) {
        this.req = req;
    }
    
}
