package command;

import io.atomix.copycat.Command;
import servidor.EnviarReply;
import servidor.EnviarRequest;

public class EnviarCommand implements Command<EnviarReply> {

    public EnviarRequest req;

    public EnviarCommand(EnviarRequest req) {
        this.req = req;
    }

}
