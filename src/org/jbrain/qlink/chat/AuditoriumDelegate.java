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
	Created on Jul 28, 2005
	
 */
package org.jbrain.qlink.chat;

import java.util.*;

import org.apache.log4j.Logger;

class Question {
	private String _sName;
	private String[] _question;
	
	public Question(String name, String[] question) {
		_sName=name;
		_question=question;
	}

	/**
	 * @return
	 */
	public String[] getQuestion() {
		return _question;
	}

	/**
	 * @return
	 */
	public String getHandle() {
		return _sName;
	}
	
}
class AuditoriumDelegate extends RoomDelegate {
	private static Logger _log=Logger.getLogger(AuditoriumDelegate.class);
	private List _queue=Collections.synchronizedList(new ArrayList());
	private static final String SYS_NAME = "AudManager";
	private boolean _bAcceptingQuestions = false;
	private List _alRegList=Collections.synchronizedList(new ArrayList()); 
	
	/**
	 * @param name
	 */
	public AuditoriumDelegate(String name) {
		super(name,true,true);
	}
	
	public boolean isFull() {
		return getPopulation()==ROOM_CAPACITY-1;
	}

	public synchronized void queue(String handle, String[] question) {
		_queue.add(new Question(handle,question));
		if(_alRegList.size()>0) {
			ArrayList alMsg=new ArrayList();
			for(int i=0,size=_alRegList.size();i<size;i++) {
				privmsgQuestion(alMsg,size-1);
				sendSystemMessage(SYS_NAME,((Integer)_alRegList.get(i)).intValue(),alMsg);
			}
		}
	}
	
	public void removeUser(int seat) {
		_alRegList.remove(new Integer(seat));
		super.removeUser(seat);
	}
	
	public void say(int seat, String text) {
		if(text.startsWith("//")) {
			ArrayList alMsg=new ArrayList();
			String[] cmdline=text.split(" ");
			String cmd=cmdline[0].toLowerCase();
			int pos=0;
			if(cmd.startsWith("//sho") || cmd.startsWith("//air")) {
				// show
				if(cmdline.length>1)
					pos=getNumber(cmdline[1]);
				if(pos>-1 && pos < _queue.size()) {
					_log.debug("Showing question: " + pos);
					Question q=(Question)_queue.remove(pos);
					for(int i=0,size=q.getQuestion().length;i<size;i++)
						processEvent(new ChatEvent(this,q.getHandle(),q.getQuestion()[i]));
				}
			} else if(cmd.startsWith("//get")) {
				// get
				if(cmdline.length>1)
					pos=getNumber(cmdline[1]);
				if(pos>-1 && pos < _queue.size()) {
					_log.debug("Retrieving question: " + pos);
					privmsgQuestion(alMsg,pos);
					sendSystemMessage(SYS_NAME,seat,alMsg);
				}
			} else if(cmd.startsWith("//del") || cmd.startsWith("//rem")) {
				// delete
				if(cmdline.length>1)
					pos=getNumber(cmdline[1]);
				if(pos>-1 && pos < _queue.size()) {
					_log.debug("Deleting question: " + pos);
					Question q=(Question)_queue.remove(pos);
					alMsg.add("Question " + pos + " deleted.");
					sendSystemMessage(SYS_NAME,seat,alMsg);
				}
			} else if(cmd.startsWith("//lis")) {
				// list
				if(cmdline.length>1)
					pos=getNumber(cmdline[1]);
				if(pos>-1 && pos < _queue.size()) {
					_log.debug("Listing questions starting with: " + pos);
					int max=pos+4;
					for(;pos<_queue.size()&&pos<max;pos++)
						privmsgQuestion(alMsg,pos);
					sendSystemMessage(SYS_NAME,seat,alMsg);
				}
			} else if(cmd.startsWith("//cou")) {
				// count
				_log.debug("Sending count of queued questions: " + _queue.size());
				alMsg.add("There are " + _queue.size() + " questions in the queue.");
				sendSystemMessage(SYS_NAME,seat,alMsg);
			} else if(cmd.startsWith("//cle")) {
				// clear Q
				_log.debug("Clearing question queue");
				_queue.clear();
				alMsg.add("Queue now empty.");
				sendSystemMessage(SYS_NAME,seat,alMsg);
			} else if(cmd.startsWith("//acc")){
				_log.debug("Accepting questions");
				_bAcceptingQuestions=true;
				processEvent(new QuestionStateEvent(this,QuestionStateEvent.ACCEPTING_QUESTIONS));
			} else if(cmd.startsWith("//rej")){
				_log.debug("Rejecting questions");
				_bAcceptingQuestions=false;
				processEvent(new QuestionStateEvent(this,QuestionStateEvent.NOT_ACCEPTING_QUESTIONS));
			} else if(cmd.startsWith("//reg")){
				_log.debug("Registering for notifications");
				_alRegList.add(new Integer(seat));
			} else if(cmd.startsWith("//unr")){
				_log.debug("De-Registering for notifications");
				_alRegList.remove(new Integer(seat));
			} else {
				super.say(seat,text);
			}
			
			
			// execture command
		} else
			super.say(seat,text);
	}

	/**
	 * @param sys_name2
	 * @param seat
	 * @param alMsg
	 */
	private void sendSystemMessage(String name, int seat, List l) {
		processEvent(new SystemMessageEvent(this,name,seat,(String[])l.toArray(new String[0])));
	}

	/**
	 * @param pos
	 */
	private void privmsgQuestion(List l, int pos) {
		Question q=(Question)_queue.get(pos);
		l.add("Q#: " + pos + " from " + q.getHandle() + ":");
		for(int i=0,size=q.getQuestion().length;i<size;i++)
			l.add(q.getQuestion()[i]);
	}

	/**
	 * @return
	 */
	public boolean isAcceptingQuestions() {
		return _bAcceptingQuestions = false;
	}

}
