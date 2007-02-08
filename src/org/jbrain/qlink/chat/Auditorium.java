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

public class Auditorium extends AbstractRoom {

	private String _sHandle;

	public Auditorium(AuditoriumDelegate room, String handle) {
		super(room);
		_sHandle=handle;
}

	public void say(String[] text) {
		((AuditoriumDelegate)_room).queue(_sHandle,text);
	}

	public void say(String text) {
		String[] s=new String[0];
		s[0]=text;
		say(s);
	}
	
	protected void processMessageEvent(MessageEvent event) {
		// we don't have these events in the Auditorium
	}

	public synchronized void addEventListener(RoomEventListener listener) {
		super.addEventListener(listener);
		// the this object will be off for this..
		processEvent(new QuestionStateEvent(this,(((AuditoriumDelegate)_room).isAcceptingQuestions()?QuestionStateEvent.ACCEPTING_QUESTIONS:QuestionStateEvent.NOT_ACCEPTING_QUESTIONS)));
	}

	/* (non-Javadoc)
	 * @see org.jbrain.qlink.chat.Room#createGame(java.lang.String, java.lang.String, boolean)
	 */
	public Game createGame(int id,String name, String type, boolean systemPickOrder) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.jbrain.qlink.chat.Room#getPendingGame()
	 */
	public Game getPendingGame() {
		return null;
	}

}
