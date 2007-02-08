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

	private int _seat;

	/**
	 * @param room
	 * @param user
	 */
	public NormalRoom(RoomDelegate room, int seat) {
		super(room);
		_seat=seat;
	}

	public void say(String text) {
		_room.say(_seat,text);
	}
	
	public void leave() {
		RoomManager.leaveRoom(_room,_seat);
		super.leave();
	}

	public void say(String[] text) {
		for(int i=0,size=text.length;i<size;i++) {
			say(text[i]);
		}
	}

	
	protected void processMessageEvent(MessageEvent event) {
		// is it from us?
		if(event.getOriginatingSeat()==_seat)
			super.processMessageEvent(event);
	}

	protected void processSystemMessageEvent(SystemMessageEvent event) {
		// is it to us or broadcast?
		if(event.getRecipientSeat()==_seat || event.getRecipientSeat()==SystemMessageEvent.SEAT_BROADCAST)
			super.processSystemMessageEvent(event);
	}

	public Game createGame(int id, String name, String type, boolean systemPickOrder) {
		return new Game(_room,_seat,_room.createGame(id,name,type,systemPickOrder));
	}

	/* (non-Javadoc)
	 * @see org.jbrain.qlink.chat.Room#getPendingGame()
	 */
	public Game getPendingGame() {
		GameDelegate game=_room.getGame(_seat);
		if(game!=null)
			return new Game(_room,_seat,game);
		return null;
	}

}
