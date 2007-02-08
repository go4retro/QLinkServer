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

import java.util.ArrayList;

public abstract class AbstractRoom implements Room {
	protected RoomDelegate _room;
	private ArrayList _listeners=new ArrayList();
	private RoomEventListener _listener=new RoomEventListener() {
		public void userSaid(ChatEvent event) {
			processEvent(event);
		}
		public void userJoined(JoinEvent event) {
			processEvent(event);
		}
		public void userLeft(JoinEvent event) {
			processEvent(event);
		}
		public void userSaid(MessageEvent event) {
			processEvent(event);
		}
		public void systemSent(SystemMessageEvent event) {
			processEvent(event);
		}
		public void acceptingQuestions(QuestionStateEvent event) {
			processEvent(event);
		}
		public void rejectingQuestions(QuestionStateEvent event) {
			processEvent(event);
		}
	};
	/**
	 * @param room
	 * @param user
	 */
	public AbstractRoom(RoomDelegate room) {
		_room=room;
		_room.addEventListener(_listener);
	}

	/**
	 * @return
	 */
	public String getName() {
		return _room.getName();
	}

	/**
	 * @return
	 */
	public synchronized int getPopulation() {
		return _room.getPopulation();
	}

	/**
	 * @param i
	 * @return
	 */
	public synchronized SeatInfo[] getSeatInfoList() {
		return _room.getSeatInfoList();
	}

	public synchronized void addEventListener(RoomEventListener listener) {
		_listeners.add(listener);
	}

	public synchronized void removeEventListener(RoomEventListener listener) {
		if(_listeners.contains(listener)) {
			_listeners.remove(listener);
		}
	}

	protected synchronized void processJoinEvent(JoinEvent event) {
		if(event != null && _listeners.size() > 0) {
			if(event.getType()==JoinEvent.EVENT_JOIN)
				for(int i=0,size=_listeners.size();i<size;i++) {
					((RoomEventListener)_listeners.get(i)).userJoined(event);
				}
			else
				for(int i=0,size=_listeners.size();i<size;i++) {
					((RoomEventListener)_listeners.get(i)).userLeft(event);
				}
		}
	}

	public synchronized void processQuestionStateEvent(QuestionStateEvent event) {
		if(event != null && _listeners.size() > 0) {
			if(event.getType()==QuestionStateEvent.ACCEPTING_QUESTIONS)
				for(int i=0,size=_listeners.size();i<size;i++) {
					((RoomEventListener)_listeners.get(i)).acceptingQuestions(event);
				}
			else
				for(int i=0,size=_listeners.size();i<size;i++) {
					((RoomEventListener)_listeners.get(i)).rejectingQuestions(event);
				}
		}
	}

	protected synchronized void processChatEvent(ChatEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((RoomEventListener)_listeners.get(i)).userSaid(event);
			}
		}
	}

	protected synchronized void processMessageEvent(MessageEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((RoomEventListener)_listeners.get(i)).userSaid(event);
			}
		}
	}

	protected synchronized void processSystemMessageEvent(SystemMessageEvent event) {
		if(event != null && _listeners.size() > 0) {
			for(int i=0,size=_listeners.size();i<size;i++) {
				((RoomEventListener)_listeners.get(i)).systemSent(event);
			}
		}
	}

	protected synchronized void processEvent(RoomEvent event) {
		if(event instanceof JoinEvent) 
			processJoinEvent((JoinEvent)event);
		else if(event instanceof MessageEvent) 
			processMessageEvent((MessageEvent)event);
		else if(event instanceof ChatEvent) 
			processChatEvent((ChatEvent)event);
		else if(event instanceof SystemMessageEvent) 
			processSystemMessageEvent((SystemMessageEvent)event);
		else if(event instanceof QuestionStateEvent) 
			processQuestionStateEvent((QuestionStateEvent)event);
	}

	public abstract void say(String[] text);
	public abstract void say(String text);
	
	public void leave() {
		_room.removeEventListener(_listener);
	}

	/* (non-Javadoc)
	 * @see org.jbrain.qlink.chat.Room#getSeatInfo(java.lang.String)
	 */
	public SeatInfo getSeatInfo(String handle) {
		return _room.getSeatInfo(handle);
	}
	
	public boolean isPublicRoom() {
		return _room.isPublicRoom();
	}
	
	public GameInfo[] getGameInfoList() {
		return _room.getGameInfoList();
	}
	
}
