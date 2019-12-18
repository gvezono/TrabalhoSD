package command;

import io.atomix.copycat.Query;
import servidor.ReceberReply;
import servidor.ReceberRequest;

public class ReceberQuery implements Query<ReceberReply> {

    public ReceberRequest req;

    public ReceberQuery(ReceberRequest req) {
        this.req = req;
    }
    
}
