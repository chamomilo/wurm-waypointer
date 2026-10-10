package org.waypoints.next.integration;

import com.wurmonline.client.game.*;
import com.wurmonline.shared.constants.PlayerOnlineStatus;
import org.waypoints.next.i18n.Messages;
import java.util.*;

/** Reads only friend information already delivered to the vanilla client. */
final class ClientFriendsAdapter {
    static final class Entry {
        final String name,detail;
        final boolean sameServer;
        final Boolean online;
        Entry(String name,String detail,boolean sameServer,Boolean online){this.name=name;this.detail=detail;this.sameServer=sameServer;this.online=online;}
    }
    List<Entry> snapshot(){
        List<Entry> result=new ArrayList<Entry>();
        for(Friend friend:new ArrayList<Friend>(FriendsManager.getInstance().getFriends())){
            PlayerOnlineStatus state=friend.getStatus();
            if(state==PlayerOnlineStatus.DELETE_ME||friend.getName()==null||friend.getName().isEmpty())continue;
            result.add(new Entry(friend.getName(),Messages.text(state.getName())+"; "+friend.getServerName()+"; "+friend.getNote(),state==PlayerOnlineStatus.ONLINE||state==PlayerOnlineStatus.LOST_LINK,online(state)));
        }
        return result;
    }
    static Boolean online(PlayerOnlineStatus state){
        if(state==PlayerOnlineStatus.OFFLINE)return false;
        if(state==PlayerOnlineStatus.ONLINE||state==PlayerOnlineStatus.OTHER_SERVER||state==PlayerOnlineStatus.LOST_LINK)return true;
        return null;
    }
}
