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
	private static ArrayList _alLobbies=new ArrayList();
	private static HashMap _hmUsers=new HashMap();
	private static HashMap _hmPublicRooms=new HashMap();
	private static HashMap _hmPrivateRooms=new HashMap();
	private static final String ROOM_LOBBY = "Lobby";
	private static AuditoriumDelegate _auditorium;
	
	static {
		// add a default Lobby.
		_log.debug("Creating default Lobby");
		LobbyDelegate room=new LobbyDelegate(ROOM_LOBBY,true);
		addLobby(room);
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
	/* 
	 * This code should scan through the lobbies list, trying to find the first lobby that has a slot.
	 * If it cannot find any, then it should create the first null room found, and put the person in there.
	 */
	public static synchronized Room join(String handle) {
		LobbyDelegate room=null;
		int seat=0;
		
		// any current lobbies have room?
		for(int i=0,size=_alLobbies.size();i<size;i++) {
			room=(LobbyDelegate)_alLobbies.get(i);
			if(room!= null && !room.isFull()) {
				seat=room.addUser(handle);
				break;
			} else {
				room=null;
			}
			
		}
		if(room==null) {
			int i, size;
			for(i=0,size=_alLobbies.size();i<size;i++) {
				if(_alLobbies.get(i)==null)
					break;
			}
			room=new LobbyDelegate(ROOM_LOBBY + " " + (char)(i+'@'),false);
			addLobby(room);
			seat=room.addUser(handle);
		}
		return new NormalRoom(room,seat);
	}
	
	/*
	 * We try to find the specified room, and get a seat.
	 * Questions:
	 * 
	 * If a user tries to enter a Lobby, it will work if the Lobby is present and not full
	 * If the Lobby is full, I assume we should send an error about room full?
	 * If the Lobby does not exist, should we create it, or fail?
	 * If the latter, what do we do if the user can;t get into any of the Lobbies because they are full?
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
		if(room instanceof LobbyDelegate)
			leaveLobby((LobbyDelegate)room,seat);
		else if(room.isPublicRoom()) {
			leavePublicRoom(room,seat);
		} else {
			leavePrivateRoom(room,seat);
		}
	}
	
	private static RoomDelegate getPublicRoom(String name) {
		// if the name is "Lobby...", and does not exist, add a Lobby instead.
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

	private static void leaveLobby(LobbyDelegate room, int seat) {
		room.removeUser(seat);
		if(room.getPopulation()==0) {
			// ditch the public room.
			int i=_alLobbies.indexOf(room);
			if(i>0) {
				_hmPublicRooms.remove(room.getName());
				if(i+1==_alLobbies.size())
					// we are the last one in the list, delete
					_alLobbies.remove(i);
				else
					// we are not, so leave a hole.
					_alLobbies.set(i,null);
			}
		}
	}
	
	private static void addLobby(LobbyDelegate room) {
		_alLobbies.add(room);
		addPublicRoom(room);
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
