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
	Created on Jul 27, 2005
	
 */
package org.jbrain.qlink.chat;

public class NormalRoom extends AbstractRoom{

	/**
	 * @param room
	 * @param user
	 */
	public NormalRoom(QRoom room, QSeat user) {
		super(room,user);
	}

	public void say(String text) {
		_room.say(_user,text);
	}
	
	public void leave() {
		RoomManager.leaveRoom(_room,_user);
		super.leave();
	}

	public void say(String[] text) {
		for(int i=0,size=text.length;i<size;i++) {
			say(text[i]);
		}
	}

	/**
	 * @param i
	 * @return
	 */
	public synchronized QSeat[] getSeatInfoList() {
		return _room.getSeatInfoList(_user);
	}

	
	public Game createGame(int id, String name, String type, boolean systemPickOrder) {
		return new Game(_room,_user,_room.createGame(id,name,type,systemPickOrder));
	}

	/* (non-Javadoc)
	 * @see org.jbrain.qlink.chat.Room#getPendingGame()
	 */
	public Game getPendingGame() {
		GameDelegate game=_room.getGame(_user);
		if(game!=null)
			return new Game(_room,_user,game);
		return null;
	}

	/* (non-Javadoc)
	 * @see org.jbrain.qlink.chat.Room#getInfo()
	 */
	public String getInfo() {
		return _room.getInfo();
	}

	/* (non-Javadoc)
	 * @see org.jbrain.qlink.chat.Room#getExtSeatInfoList()
	 */
	public QSeat[] getExtSeatInfoList() {
		return _room.getExtSeatInfoList(_user);
	}
}
