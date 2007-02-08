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
	Created on Sep 3, 2005
	
 */
package org.jbrain.qlink.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

public class GameDelegate {
	private static Logger _log=Logger.getLogger(GameDelegate.class);
	private String _sName;
	private String _sType;
	private boolean _bSystemPickOrder;

	/**
	 * 
	 * @uml.property name="_room"
	 * @uml.associationEnd inverse="_userGame:org.jbrain.qlink.chat.RoomDelegate" multiplicity=
	 * "(1 1)"
	 */
	private RoomDelegate _room;

	/**
	 * 
	 * @uml.property name="_alPlayers"
	 * @uml.associationEnd elementType="org.jbrain.qlink.chat.SeatInfo" multiplicity="(0
	 * -1)"
	 */
	private ArrayList _alPlayers = new ArrayList();

	private byte[] _seats;

	/**
	 * 
	 * @uml.property name="_listeners"
	 * @uml.associationEnd elementType="org.jbrain.qlink.chat.GameEventListener" multiplicity=
	 * "(0 -1)"
	 */
	private ArrayList _listeners = new ArrayList();

	private int _iID;

	/**
	 * 
	 * @uml.property name="_alDeclineList"
	 * @uml.associationEnd elementType="org.jbrain.qlink.chat.SeatInfo" multiplicity="(0
	 * -1)"
	 */
	private ArrayList _alDeclineList = new ArrayList();

	/**
	 * 
	 * @uml.property name="_alAcceptList"
	 * @uml.associationEnd elementType="org.jbrain.qlink.chat.SeatInfo" multiplicity="(0
	 * -1)"
	 */
	private ArrayList _alAcceptList = new ArrayList();

	/**
	 * 
	 * @uml.property name="_alAbstainList"
	 * @uml.associationEnd elementType="org.jbrain.qlink.chat.SeatInfo" multiplicity="(0
	 * -1)"
	 */
	private ArrayList _alAbstainList = new ArrayList();

	private boolean _bActive=true;

	/**
	 * @param name
	 * @param type
	 * @param systemPickOrder
	 */
	public GameDelegate(RoomDelegate room, int id, String name, String type, boolean systemPickOrder) {
		_iID=id;
		_sName=name;
		_sType=type;
		_bSystemPickOrder=systemPickOrder;
		_room=room;
		_log.info("Creating new instance of " + name);
	}

	/**
	 * @param seat
	 * @throws UserMismatchException
	 * @throws UserNotInRoomException
	 */
	public boolean addPlayer(String handle) throws UserNotInRoomException {
		SeatInfo info;
		synchronized(_room) {
			info=_room.addUserToGame(handle,this);
			if(info==null) {
				_log.debug("Could not add '" + handle + "' to game: " + _sName);
				return false;
			} else {
				// add to unacked invite list.
				synchronized(_alDeclineList) {
					_alPlayers.add(info);
					_alAbstainList.add(info);
				}
				return true;
			}
		}
	}

	/**
	 * @return
	 */
	public byte[] getPlayOrder() {
		List l;
		Random r=new Random();
		
		synchronized(_alDeclineList) {
			if(_bSystemPickOrder) {
				List orig=new ArrayList(_alPlayers);
				l=new ArrayList();
				while(orig.size()>0) {
					l.add(orig.remove(r.nextInt(orig.size())));
				}
			} else {
				l=_alPlayers;
			}
		}
		// if we need to sort, do it here.
		if(_seats==null) {
			_seats=new byte[l.size()];
			for(int i=0;i<_seats.length;i++) {
				_seats[i] = (byte)((SeatInfo)l.get(i)).getSeat();
			}
		} 
		return _seats;
	}

	/**
	 * @return
	 */
	public SeatInfo[] getPlayers() {
		return (SeatInfo[])_alPlayers.toArray(new SeatInfo[0]);
	}

	/**
	 * 
	 */
	public void acceptInvite(int seat) {
		SeatInfo info=_room.getSeatInfo(seat);
		addAccept(info);
		processEvent(new GameEvent(this,GameEvent.ACCEPT_INVITE, seat, info.getHandle()));
	}

	/**
	 * @param info
	 */
	private void addAccept(SeatInfo info) {
		synchronized(_alDeclineList) {
			_alAcceptList.add(info);
			_alAbstainList.remove(info);
		}
	}

	public void declineInvite(int seat) {
		SeatInfo info=_room.getSeatInfo(seat);
		addDecline(info);
		processEvent(new GameEvent(this,GameEvent.DECLINE_INVITE, seat, info.getHandle()));
	}

	/**
	 * @param info
	 */
	private void addDecline(SeatInfo info) {
		synchronized(_alDeclineList) {
			_alDeclineList.add(info);
			_alAbstainList.remove(info);
		}
	}

	/**
	 * @param _listener
	 */
	public void addListener(GameEventListener listener) {
		synchronized(_listeners) {
			_listeners.add(listener);
		}
	}

	protected void processEvent(RoomEvent event) {
		synchronized(_listeners) {
			if(event instanceof GameCommEvent) 
				processGameEvent((GameCommEvent)event);
			else if(event instanceof GameEvent) 
				processGameLoadEvent((GameEvent)event);
			else if(event instanceof GameTerminationEvent) 
				processGameTerminationEvent((GameTerminationEvent)event);
		}
	}
	
	public boolean canContinue() {
		return _alAcceptList.size()==_alPlayers.size();
	}

	/**
	 * @param event
	 */
	protected void processGameEvent(GameCommEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((GameEventListener)_listeners.get(i)).gameSent(event);
			}
		}
	}

	protected void processGameLoadEvent(GameEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((GameEventListener)_listeners.get(i)).eventOccurred(event);
			}
		}
	}

	protected void processGameTerminationEvent(GameTerminationEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((GameEventListener)_listeners.get(i)).gameTerminated(event);
			}
		}
	}

	/**
	 * @return
	 */
	public String getType() {
		return _sType;
	}

	/**
	 * @return
	 */
	public boolean isSystemPickingOrder() {
		return _bSystemPickOrder;
	}

	/**
	 * @return
	 */
	public String getName() {
		return _sName;
	}

	/**
	 * @return
	 */
	public int getID() {
		return _iID;
	}

	public void terminate() {
		_log.info("Terminating instance of " + _sName);
		// let everyone watching this game know.
		processEvent(new GameTerminationEvent(this));
		synchronized(_alDeclineList) {
			while(_alPlayers.size()>0)
				removePlayer((SeatInfo)_alPlayers.get(0));
		}
		synchronized(_listeners) {
			_listeners.clear();
		}
		_room.destroyGame(this);
		_bActive=false;
	}

	/**
	 * @param listener
	 */
	public void removeListener(GameEventListener listener) {
		synchronized(_listeners) {
			_listeners.remove(listener);
		}
		
	}

	/**
	 * @param seat
	 * @param text
	 */
	public void send(int seat, String text) {
		processEvent(new GameCommEvent(this,seat,_room.getSeatInfo(seat).getHandle(),text));
	}

	/**
	 * @param seat
	 */
	public void requestLoad(int seat) {
		SeatInfo info=_room.getSeatInfo(seat);
		clearVotes();
		processEvent(new GameEvent(this,GameEvent.REQUEST_LOAD, seat, info.getHandle()));
	}

	public void readyToStart(int seat) {
		SeatInfo info=_room.getSeatInfo(seat);
		addAccept(info);
		processEvent(new GameEvent(this,GameEvent.READY_TO_START, seat, info.getHandle()));
	}

	/**
	 * 
	 */
	public void requestRestart(int seat) {
		SeatInfo info= _room.getSeatInfo(seat);
		clearVotes();
		// we alreayd want to restart...
		addAccept(info);
		processEvent(new GameEvent(this,GameEvent.REQUEST_RESTART, seat,info.getHandle()));
	}

	/**
	 * 
	 */
	public void clearVotes() {
		synchronized(_alDeclineList) {
			_alDeclineList.clear();
			_alAcceptList.clear();
			_alAbstainList.clear();
			_alAbstainList.addAll(_alPlayers);
		}
	}

	/**
	 * @param seat
	 */
	public void acceptRestart(int seat) {
		SeatInfo info= _room.getSeatInfo(seat);
		addAccept(info);
		processEvent(new GameEvent(this,GameEvent.ACCEPT_RESTART, seat, info.getHandle()));
	}

	/**
	 * @return
	 */
	public List getAbstainList() {
		return Collections.unmodifiableList(_alAbstainList);
	}

	/**
	 * @param handle
	 * 
	 */
	public void leave(int seat) {
		SeatInfo info= _room.getSeatInfo(seat);
		if(info!=null) {
			_log.debug("Removing '" + info.getHandle() + "' from game: " + _sName);
			removePlayer(info);
			processEvent(new GameEvent(this,GameEvent.LEAVE_GAME, info.getSeat(), info.getHandle()));
			synchronized(_alDeclineList) {
				// is everyone gone?
				if(_alPlayers.size()==0)
					terminate();
			}
		} else {
			_log.warn("Player '" + info.getHandle() + "' not in game: " + _sName);
		}
	}

	/**
	 * @param seat
	 */
	private void removePlayer(SeatInfo info) {
		_room.removeUserFromGame(info.getHandle());
		synchronized(_alDeclineList) {
			_alPlayers.remove(info);
			_alAbstainList.remove(info);
			_alDeclineList.remove(info);
			_alAcceptList.remove(info);
		}
	}

	/**
	 * @param seat
	 */
	public void restart(int seat) {
		SeatInfo info= _room.getSeatInfo(seat);
		// we need to re-order the players to have this person first.
		// for now, let's just shift everyone up by 1
		int i=1;
		while(_seats[i]!=info.getSeat() && i<_seats.length) {
			_seats[i] = _seats[i-1];
		}
		_seats[0]=(byte)info.getSeat();
		processEvent(new GameEvent(this,GameEvent.RESTART_GAME, seat, info.getHandle()));
	}

	/**
	 * @return
	 */
	public boolean isActive() {
		return _bActive;
	}

	/**
	 * 
	 */
	public void start(int seat) {
		SeatInfo info= _room.getSeatInfo(seat);
		processEvent(new GameEvent(this,GameEvent.START_GAME, seat, info.getHandle()));
		
	}

	/**
	 * @param seat
	 */
	public void declineRestart(int seat) {
		SeatInfo info=_room.getSeatInfo(seat);
		addDecline(info);
		processEvent(new GameEvent(this,GameEvent.DECLINE_RESTART, seat, info.getHandle()));
		
	}
}
