/*
	Copyright Jim Brain and Brain Innovations, 2005.

	This file is part of QLinkServer.

	QLinkServer is free software; you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation; either version 2 of the License, or
	(at your option) any later version.

	QLinkServer is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with QLinkServer; if not, write to the Free Software
	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

	@author Jim Brain
	Created on Jul 26, 2005
	
 */
package org.jbrain.qlink.chat;

import java.util.*;

import org.apache.log4j.Logger;
import org.jbrain.qlink.chat.irc.simple.IRCRoomDelegate;
import org.jbrain.qlink.user.QHandle;


/*
 * Normally, this would be a singleton, but then you'd need to synchronize on the class, not the instance,
 * so this seemed easier.  I may change it later
 */
public class RoomManager {
	private static Logger _log=Logger.getLogger(RoomManager.class);
	private static Hashtable _htPublicRooms=new Hashtable();
	private static Hashtable _htPrivateRooms=new Hashtable();
	private static final String ROOM_LOBBY = "Lobby";
	private static AuditoriumDelegate _auditorium;
	
	static {
		// add a default Lobby.
		_log.debug("Creating default Lobby");
		addPublicRoom(new RoomDelegate(ROOM_LOBBY,true,true));
		_log.debug("Creating Auditorium");
		_auditorium=new AuditoriumDelegate("Auditorium");
		addPrivateRoom(_auditorium);
		
		// temp people for testing
		/*ChatProfile p=new ChatProfile();
		join(new QHandle("Person 1"),p);
		join(new QHandle("Person 2"),p);
		join(new QHandle("Person 3"),p);
		join(new QHandle("Person 4"),p);
		join(new QHandle("Person 5"),p);
		join(new QHandle("Person 6"),p);
		join(new QHandle("Person 7"),p);
		join(new QHandle("Person 8"),p);
		join(new QHandle("Person 9"),p);
		join(new QHandle("Person 10"),p);*/
	}

	// does not need to be sync
	public static Room join(QHandle handle,ChatProfile profile) {
		String name=ROOM_LOBBY;
		Room room=null;
		int i=(int)'A';
		
		do {
			room=joinRoom(name,handle,profile,true);
			if(i<='Z')
				name=ROOM_LOBBY + " " + (char)(i++);
			else {
				name=ROOM_LOBBY + " " + (int)(i-'Z');
				i++;
			}
		} while(room == null);
		return room;
	}
	
	/*
	 * We try to find the specified room, and get a seat.
	 * Questions:
	 * 
	 * If a user tries to enter a Lobby, it will work if the Lobby is present and not full
	 * If the Lobby is full, it will return null...
	 */
	// this needs to be sync
	public synchronized static Room joinRoom(String name, QHandle handle, ChatProfile profile, boolean bPublic) {
		QRoom room;
		QSeat user=null;
		
		if(bPublic)
			room=getPublicRoom(name);
		else
			room=getPrivateRoom(name);
		if(!room.isFull()) {
			user=room.addUser(handle,profile);
		} else {
			room=null;
		}
		if(room==null)
			return null;
		return new NormalRoom(room,user); 
	}

	// no need for sync
	public static AbstractRoom joinAuditorium(QHandle handle, ChatProfile profile) {
		QSeat user=_auditorium.addViewer(handle,profile);
		return new Auditorium(_auditorium,user);
	}

	// no need for sync
	public static void leaveAuditorium(QHandle handle) {
		_auditorium.removeViewer(handle);
	}
	
	// must be synchronized
	public synchronized static RoomInfo[] getRoomList() {
		RoomInfo info[]=new RoomInfo[_htPublicRooms.size()];
		QRoom room;
		Iterator i=_htPublicRooms.values().iterator();
		int pos=0;
		while(i.hasNext()) {
			room=(QRoom)i.next();
			info[pos++]=new RoomInfo(room.getName(),room.getPopulation(), true);
		}
		return info;
	}

	public synchronized static RoomInfo getUserLocation(QHandle handle) {
		RoomInfo room=getUserLocation(_htPublicRooms,handle);
		if(room==null)
			room=getUserLocation(_htPrivateRooms,handle);
		return room;
	}

	synchronized static void leaveRoom(QRoom room, QSeat user) {
		if(room.isPublicRoom()) {
			leavePublicRoom(room,user);
		} else {
			leavePrivateRoom(room,user);
		}
	}
	
	private static QRoom getPublicRoom(String name) {
		QRoom room=(QRoom)_htPublicRooms.get(name.toLowerCase());
		if(room==null) {
			room=createRoom(name,true);
			addPublicRoom(room);
		}
		return room;
	}

	/**
	 * @param name
	 * @param b
	 */
	private static QRoom createRoom(String name, boolean b) {
		QRoom room;
		
		if(b && name.toLowerCase().startsWith("irc ") && name.length()>4) {
			room=new IRCRoomDelegate(name,b,false);
		} else
			room=new RoomDelegate(name,b,false);
		return room;
	}

	private static QRoom getPrivateRoom(String name) {
		QRoom room=(QRoom)_htPrivateRooms.get(name.toLowerCase());
		if(room==null) {
			room=createRoom(name,false);
			addPrivateRoom(room);
		}
		return room;
	}
	private static void addPublicRoom(QRoom room) {
		_log.debug("Adding room '" + room.getName() + "' to public room list");
		_htPublicRooms.put(room.getName().toLowerCase(),room);
	}
	
	private static void addPrivateRoom(QRoom room) {
		_log.debug("Adding room '" + room.getName() + "' to private room list");
		_htPrivateRooms.put(room.getName().toLowerCase(),room);
	}
	
	private static void leavePublicRoom(QRoom room, QSeat user) {
		room.removeUser(user);
		if(room.getPopulation()==0 && !room.isLocked()) {
			removePublicRoom(room);
		}
	}
	
	/**
	 * @param room
	 */
	private static void removePublicRoom(QRoom room) {
		_log.debug("Removing room '" + room.getName() + "' from public room list");
		_htPublicRooms.remove(room.getName().toLowerCase());
		
	}

	private static void leavePrivateRoom(QRoom room, QSeat user) {
		room.removeUser(user);
		if(room.getPopulation()==0 && !room.isLocked())
			removePublicRoom(room);
	}

	private static void removePrivateRoom(QRoom room) {
		_log.debug("Removing room '" + room.getName() + "' from private room list");
		_htPrivateRooms.remove(room.getName());
		
	}

	private static RoomInfo getUserLocation(Hashtable hm,QHandle handle) {
		Iterator i=hm.values().iterator();
		QRoom room;
		while(i.hasNext()) {
			room=(QRoom)i.next();
			if(room.getSeatInfo(handle)!=null)
				return new RoomInfo(room.getName(),room.getPopulation(),room.isPublicRoom());
		}
		return null;
	}
}
