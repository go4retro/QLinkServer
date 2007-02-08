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
package org.jbrain.qlink.dialog;

import java.util.*;

import org.jbrain.qlink.cmd.action.*;
import org.jbrain.qlink.text.TextFormatter;


public abstract class AbstractDialog {
	public static final int FORMAT_CENTERED = TextFormatter.FORMAT_CENTERED;
	public static final int FORMAT_NONE = TextFormatter.FORMAT_NONE;
	public static final int FORMAT_JUSTIFIED = TextFormatter.FORMAT_JUSTIFIED;

	private String _sName;
	protected TextFormatter _text;
	protected int _iFormat;
	private boolean _bLogin;

	public AbstractDialog(String name, boolean bLogin) {
		this(name, bLogin, FORMAT_NONE);
	}
	
	public AbstractDialog(String name, boolean bLogin, int format) {
		_sName=name;
		_text=new TextFormatter(format);
		_iFormat=format;
		_bLogin=bLogin;
	}
	
	public String getName() {
		return _sName;
	}

	public void addText(String text) {
		_text.add(text);
	}
	
	public Action getPrepAction() {
		if(_bLogin)
			return new CreateLoginDialog(_sName);
		else
			return new CreateChatDialog(_sName);
	}

	public Action[] getTextActions() {
		List l=_text.getList();
		int size=l.size()-1;
		Action[] a=new Action[size];
		for(int i=0;i<size;i++) {
			if(_bLogin)
				a[i]=new LoginDialogText(_sName,(String)l.get(i));
			else
				a[i]=new ChatDialogText(_sName,(String)l.get(i));
		}
		return a;
	}

	/**
	 * @return
	 */
	public abstract Action getResponseAction();
	
}
