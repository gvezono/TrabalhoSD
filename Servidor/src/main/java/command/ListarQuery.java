package command;

import io.atomix.copycat.Query;
import servidor.ListarReply;
import servidor.ListarRequest;

public class ListarQuery implements Query<ListarReply>{
    
    public ListarRequest req;
    
    public ListarQuery(ListarRequest req) {
        this.req = req;
    }

}
