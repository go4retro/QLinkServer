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
	Created on Sep 6, 2005
	
 */
package org.jbrain.qlink.chat;

import java.util.List;


public class Game {

	private GameDelegate _game;
	private int _iSeat;
	private RoomDelegate _room;

	/**
	 * @param _seat
	 * @param delegate
	 */
	public Game(RoomDelegate room, int seat, GameDelegate delegate) {
		_game=delegate;
		_iSeat=seat;
		_room=room;
	}

	/**
	 * 
	 */
	public void acceptInvite() {
		_game.acceptInvite(_iSeat);
	}

	/**
	 * 
	 */
	public void declineInvite() {
		_game.declineInvite(_iSeat);
	}

	/**
	 * @param _listener
	 */
	public void addListener(GameEventListener listener) {
		_game.addListener(listener);
		
	}

	/**
	 * @param string
	 */
	public boolean addPlayer(String string) {
		SeatInfo info=_room.getSeatInfo(string);
		if(info != null) {
			_game.addPlayer(info.getSeat());
			return true;
		}
		return false;
	}

	/**
	 * @return
	 */
	public SeatInfo[] getPlayers() {
		return _game.getPlayers();
	}

	/**
	 * @return
	 */
	public byte[] getPlayOrder() {
		return _game.getPlayOrder();
	}

	/**
	 * 
	 */
	public void terminate() {
		_game.terminate();
	}

	/**
	 * 
	 */
	public void clearPlayers() {
		_game.clearPlayers();
		
	}

	/**
	 * @param _listener
	 */
	public void removeListener(GameEventListener listener) {
		_game.removeListener(listener);
		
	}

	/**
	 * @return
	 */
	public boolean canContinue() {
		return _game.canContinue();
	}

	/**
	 * @param text
	 */
	public void send(String text) {
		_game.send(_iSeat,text);
	}

	/**
	 * 
	 */
	public void requestRestart() {
		_game.requestRestart(_iSeat);
		
	}

	/**
	 * 
	 */
	public void acceptRestart() {
		_game.acceptRestart(_iSeat);
		
	}

	/**
	 * 
	 */
	public void requestLoad() {
		_game.requestLoad(_iSeat);
	}

	/**
	 * 
	 */
	public void readyToStart() {
		_game.readyToStart(_iSeat);
	}

	public List getAbstainList() {
		return _game.getAbstainList();
	}

	/**
	 * 
	 */
	public void leave() {
		_game.leave(_iSeat);
		
	}

}
