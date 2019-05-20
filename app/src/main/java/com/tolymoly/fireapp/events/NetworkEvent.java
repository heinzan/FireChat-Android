package com.tolymoly.fireapp.events;

import com.tolymoly.fireapp.model.realms.Message;

/**
 * Created by Devlomi on 18/03/2018.
 */

public class NetworkEvent {
    private Message message;
    private boolean compress;

    public NetworkEvent(Message message) {
        this.message = message;
        this.compress = compress;
    }

    public Message getMessage() {
        return message;
    }

}
