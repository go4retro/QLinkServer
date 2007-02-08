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


/*
 * Normally, this would be a singleton, but then you'd need to synchronize on the class, not the instance,
 * so this seemed easier.  I may change it later
 */
public class RoomManager {
	private static Logger _log=Logger.getLogger(RoomManager.class);
	//private static ArrayList _alLobbies=new ArrayList();
	private static HashMap _hmUsers=new HashMap();
	private static HashMap _hmPublicRooms=new HashMap();
	private static HashMap _hmPrivateRooms=new HashMap();
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
		/*join("Person 1");
		join("Person 2");
		join("Person 3");
		join("Person 4");
		join("Person 5");
		join("Person 6");
		join("Person 7");
		join("Person 8");
		join("Person 9");*/
	}

	public static synchronized Room join(String handle) {
		String name=ROOM_LOBBY;
		Room room=null;
		int i=(int)'A';
		
		do {
			room=joinRoom(name,handle,true);
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
	 * If the Lobby is full, it will generate an error
	 */
	public static synchronized Room joinRoom(String name, String handle, boolean bPublic) {
		RoomDelegate room;
		int user=0;
		
		if(bPublic)
			room=getPublicRoom(name);
		else
			room=getPrivateRoom(name);
		if(!room.isFull()) {
			user=room.addUser(handle);
		} else {
			room=null;
		}
		if(room==null)
			return null;
		return new NormalRoom(room,user); 
	}
	
	public synchronized static RoomInfo[] getRoomList() {
		RoomInfo info[]=new RoomInfo[_hmPublicRooms.size()];
		RoomDelegate room;
		Iterator i=_hmPublicRooms.values().iterator();
		int pos=0;
		while(i.hasNext()) {
			room=(RoomDelegate)i.next();
			info[pos++]=new RoomInfo(room.getName(),room.getPopulation(), false);
		}
		return info;
	}

	synchronized static void leaveRoom(RoomDelegate room, int seat) {
		if(room.isPublicRoom()) {
			leavePublicRoom(room,seat);
		} else {
			leavePrivateRoom(room,seat);
		}
	}
	
	private static RoomDelegate getPublicRoom(String name) {
		RoomDelegate room=(RoomDelegate)_hmPublicRooms.get(name.toLowerCase());
		if(room==null) {
			room=new RoomDelegate(name,true,false);
			addPublicRoom(room);
		}
		return room;
	}

	private static RoomDelegate getPrivateRoom(String name) {
		RoomDelegate room=(RoomDelegate)_hmPrivateRooms.get(name.toLowerCase());
		if(room==null) {
			room=new RoomDelegate(name,false,false);
			addPrivateRoom(room);
		}
		return room;
	}
	private static void addPublicRoom(RoomDelegate room) {
		_hmPublicRooms.put(room.getName().toLowerCase(),room);
	}
	
	private static void addPrivateRoom(RoomDelegate room) {
		_hmPrivateRooms.put(room.getName().toLowerCase(),room);
	}
	
	private static void leavePublicRoom(RoomDelegate room, int seat) {
		room.removeUser(seat);
		if(room.getPopulation()==0 && !room.isLocked())
			_hmPublicRooms.remove(room.getName().toLowerCase());
	}
	
	private static void leavePrivateRoom(RoomDelegate room, int seat) {
		room.removeUser(seat);
		if(room.getPopulation()==0 && !room.isLocked())
			_hmPrivateRooms.remove(room.getName());
	}

	/**
	 * @param handle
	 * @return
	 */
	public static AbstractRoom getAuditorium(String handle) {
		return new Auditorium(_auditorium,handle);
	}

	
	private synchronized static RoomInfo getUserLocation(HashMap hm,String name) {
		Iterator i=hm.values().iterator();
		RoomDelegate room;
		SeatInfo[] seats;
		RoomInfo info;
		Iterator i2;
		SeatInfo seat;
		while(i.hasNext()) {
			room=(RoomDelegate)i.next();
			seats=room.getSeatInfoList();
			for(int j=0;j<seats.length;j++) {
				if(seats[j].getHandle().equalsIgnoreCase(name))
					return new RoomInfo(room.getName(),room.getPopulation(),room.isPublicRoom());
			}
		}
		return null;
	}

	public synchronized static RoomInfo getUserLocation(String name) {
		RoomInfo room=getUserLocation(_hmPublicRooms,name);
		if(room==null)
			room=getUserLocation(_hmPrivateRooms,name);
		return room;
	}
}
