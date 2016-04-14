/*--------------------------------------------------------------------------* | Copyright (C) 2008  Christopher Kohlhaas                                 |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.client.internal;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.CalendarRefreshEvent;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.view.TreeFactoryImpl;
import org.rapla.client.swing.toolkit.PopupEvent;
import org.rapla.client.swing.toolkit.PopupListener;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class ConflictSelectionPresenter extends RaplaGUIComponent implements RaplaWidget {
	public RaplaTree treeSelection = new RaplaTree();
	protected final CalendarSelectionModel model;
	protected JPanel content = new JPanel();
	JLabel summary = new JLabel();
	Collection<Conflict> conflicts;
	Listener listener = new Listener();
    private final TreeFactory treeFactory;
    private final EventBus eventBus;
    private final DialogUiFactoryInterface dialogUiFactory;


    @Inject
    public ConflictSelectionPresenter(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, final CalendarSelectionModel model,
            TreeFactory treeFactory, EventBus eventBus, DialogUiFactoryInterface dialogUiFactory) throws RaplaInitializationException{
        super(facade, i18n, raplaLocale, logger);
        this.model = model;
        this.treeFactory = treeFactory;
        this.eventBus = eventBus;
        this.dialogUiFactory = dialogUiFactory;
        try
        {
            conflicts = new LinkedHashSet<Conflict>(getQuery().getConflicts());
            updateTree();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
        final JTree navTree = treeSelection.getTree();
        content.setLayout(new BorderLayout());

        content.add(treeSelection);
        // content.setPreferredSize(new Dimension(260,400));
        content.setBorder(BorderFactory.createRaisedBevelBorder());
        JTree tree = treeSelection.getTree();
        //tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(getTreeFactory().createConflictRenderer());
        tree.setSelectionModel(((TreeFactoryImpl) getTreeFactory()).createConflictTreeSelectionModel());
        treeSelection.addPopupListener(listener);
        navTree.addTreeSelectionListener(listener);
    }

    class Listener implements TreeSelectionListener, PopupListener
    {
        public void valueChanged(TreeSelectionEvent e) 
        {
            Collection<Conflict> selectedConflicts = getSelectedConflicts();
            showConflicts(selectedConflicts);
        }

        @Override
        public void showPopup( PopupEvent evt ) {
            //disabled conflict until storage is implemented 
            showTreePopup( evt );
        }
    }
   
    
    protected void showTreePopup(PopupEvent evt) {
        try {
            Point p = evt.getPoint();
            // Object obj = evt.getSelectedObject();
            List<?> list = treeSelection.getSelectedElements();
            final List<Conflict> enabledConflicts = new ArrayList<Conflict>();
            final List<Conflict> disabledConflicts = new ArrayList<Conflict>();
            for ( Object selected:list)
            {
                if ( selected instanceof Conflict)
                {
                    Conflict conflict = (Conflict) selected;
                    if ( conflict.checkEnabled())
                    {
                        enabledConflicts.add( conflict );
                    }
                    else
                    {
                        disabledConflicts.add( conflict );
                    }
                }
            }
            
            RaplaPopupMenu menu = new RaplaPopupMenu();
            RaplaMenuItem disable = new RaplaMenuItem("disable");
            disable.setText(getString("disable_conflicts"));
            disable.setEnabled( enabledConflicts.size() > 0);
            RaplaMenuItem enable = new RaplaMenuItem("enable");
            enable.setText(getString("enable_conflicts"));
            enable.setEnabled( disabledConflicts.size() > 0);
            
            disable.addActionListener( new ActionListener() {
                
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        CommandUndo<RaplaException> command = new ConflictEnable(enabledConflicts, false);
                        CommandHistory commanHistory = getCommandHistory();
                        commanHistory.storeAndExecute( command);
                    } catch (RaplaException ex) {
                        dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
                    }
                }


            });
            
            enable.addActionListener( new ActionListener() {
                
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        CommandUndo<RaplaException> command = new ConflictEnable(disabledConflicts, true);
                        CommandHistory commanHistory = getCommandHistory();
                        commanHistory.storeAndExecute( command);
                    } catch (RaplaException ex) {
                        dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
                    }
                    
                }
            });

            menu.add(disable);
            menu.add(enable);
            JComponent component = (JComponent) evt.getSource();

            menu.show(component, p.x, p.y);
        } catch (Exception ex) {
            dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
        }
    }

    public CommandHistory getCommandHistory()
    {
        return getUpdateModule().getCommandHistory();
    }

    public class ConflictEnable implements CommandUndo<RaplaException> {
        boolean enable;
        Collection<String> conflictStrings;
        
        public ConflictEnable(List<Conflict> conflicts, boolean enable) {
            
            this.enable = enable;
            conflictStrings = new HashSet<String>();
            for ( Conflict conflict: conflicts )
            {
                conflictStrings.add( conflict.getId());
            }
        }

        @Override
        public Promise<Void> execute() {
            return store_( enable);
        }
        
        @Override
        public Promise<Void> undo() {
            return store_( !enable);
        }
        @Override
        public String getCommandoName() 
        {
            return (enable ? "enable" : "disable") + " conflicts";
        }
        

        private Promise<Void> store_( boolean newFlag) {
            Collection<Conflict> conflictOrig = new ArrayList<Conflict>();
            for ( Conflict conflict:ConflictSelectionPresenter.this.conflicts)
            {
                if ( conflictStrings.contains( conflict.getId()))
                {
                    conflictOrig.add( conflict);
                }
            }
            User user;
            try
            {
                user = getUser();
            }
            catch (RaplaException e1)
            {
                return new ResolvedPromise<>(e1);
            }
            ArrayList<Conflict> conflicts = new ArrayList<Conflict>();
            for ( Conflict conflict:conflictOrig)
            {
                Conflict clone;
                try
                {
                    clone = getFacade().edit( conflict);
                }
                catch (RaplaException e)
                {
                    return new ResolvedPromise<>(e);
                }
                conflicts.add( clone);
            }
            for (Conflict conflict: conflicts)
            {
                setEnabled( ((ConflictImpl)conflict), newFlag);
            }
            final Promise<Void> store = store( conflicts, user );
            final Promise<Void> prom = store.thenAccept((a) ->
            {
                updateTree();
            });
            return prom;
        }

    }

    private Promise<Void> store(Collection<Conflict> conflicts, User user) 
    {
        return getFacade().dispatch(conflicts, Collections.emptyList(), user);
    }

    private void setEnabled(ConflictImpl conflictImpl, boolean enabled)
    {
        if ( conflictImpl.isAppointment1Editable())
        {
            conflictImpl.setAppointment1Enabled(enabled);
        }
        if ( conflictImpl.isAppointment2Editable())
        {
            conflictImpl.setAppointment2Enabled(enabled);
        }
    }

    public RaplaTree getTreeSelection() {
        return treeSelection;
    }

    protected CalendarSelectionModel getModel() {
        return model;
    }

    public void dataChanged(ModificationEvent evt) throws RaplaException {
        if ( evt == null)
        {
            return;
        }
    	TimeInterval invalidateInterval = evt.getInvalidateInterval();
    	if ( invalidateInterval != null && invalidateInterval.getStart() == null)
    	{
    		 Collection<Conflict> conflictArray = getQuery().getConflicts( );
    		 conflicts = new LinkedHashSet<Conflict>( conflictArray);
    		 updateTree();
    	}
    	else if ( evt.isModified() )
        {
        	Set<Conflict> changed = RaplaType.retainObjects(evt.getChanged(), conflicts);
            removeAll( conflicts,changed);
        	
        	removeConflict(conflicts, evt.getRemovedReferences());
        	
        	conflicts.addAll( changed);
        	for (RaplaObject obj:evt.getAddObjects())
        	{
        		if ( obj.getTypeClass()== Conflict.class)
        		{
        			Conflict conflict = (Conflict) obj;
        			conflicts.add( conflict );
        		}
        	}
        	updateTree();
        }
        else
        {
        	treeSelection.repaint();
        }
    }

    private void removeConflict(Collection<Conflict> conflicts, Set<ReferenceInfo> removedReferences)
    {
        Set<String> removedIds = new LinkedHashSet<String>();
        for (ReferenceInfo removedReference : removedReferences)
        {
            removedIds.add(removedReference.getId());
        }
        Iterator<Conflict> it = conflicts.iterator();
        while ( it.hasNext())
        {
            if ( removedIds.contains(it.next().getId()))
            {
                it.remove();
            }
        }
    }

    private void removeAll(Collection<Conflict> list,
			Set<Conflict> changed) {
    	Iterator<Conflict> it = list.iterator();
    	while ( it.hasNext())
    	{
    		if ( changed.contains(it.next()))
    		{
    			it.remove();
    		}
    	}
		
	}

	public JComponent getComponent() {
        return content;
    }
    
    final protected TreeFactory getTreeFactory() {
        return  treeFactory;
    }
    
    private void showConflicts(Collection<Conflict> selectedConflicts) {
         ArrayList<RaplaObject> arrayList = new ArrayList<RaplaObject>(model.getSelectedObjects());
         for ( Iterator<RaplaObject> it = arrayList.iterator();it.hasNext();)
         {
             RaplaObject obj = it.next();
             if (obj.getTypeClass() == Conflict.class )
             {
                 it.remove();
             }
         }
         arrayList.addAll( selectedConflicts);
         model.setSelectedObjects( arrayList);
         if (  !selectedConflicts.isEmpty() )
         {
             Conflict conflict = selectedConflicts.iterator().next();
             Date date = conflict.getStartDate();
             if ( date != null)
             {
                 model.setSelectedDate(date);
             }
         }
        eventBus.fireEvent( new CalendarRefreshEvent());
    }

    private Collection<Conflict> getSelectedConflictsInModel() {
        Set<Conflict> result = new LinkedHashSet<Conflict>();
        for (RaplaObject obj:model.getSelectedObjects())
        {
            if (obj.getTypeClass() == Conflict.class )
            {
                result.add( (Conflict) obj);
            }
        }
        return result;
    }
    
    private Collection<Conflict> getSelectedConflicts()  {
        List<Object> lastSelected = treeSelection.getSelectedElements( true);
        Set<Conflict> selectedConflicts = new LinkedHashSet<Conflict>();
        for ( Object selected:lastSelected)
        {
             if (selected instanceof Conflict)
             {
                 selectedConflicts.add((Conflict)selected );
             }
        }
        return selectedConflicts;
    }


    private void updateTree() throws RaplaException {
      
        Collection<Conflict> selectedConflicts = new ArrayList<Conflict>(getSelectedConflicts());
        Collection<Conflict> conflicts = getConflicts();
		TreeModel treeModel =  getTreeFactory().createConflictModel(conflicts);
        try {
            treeSelection.exchangeTreeModel(treeModel);
            treeSelection.getTree().expandRow(0);
        } finally {
        }
        summary.setText( getString("conflicts") + " (" + conflicts.size() + ") ");
        Collection<Conflict> inModel = new ArrayList<Conflict>(getSelectedConflictsInModel());
        if ( !selectedConflicts.equals( inModel ))
        {
            showConflicts(inModel);
        }
    }
    
    public Collection<Conflict> getConflicts() throws RaplaException {
        Collection<Conflict> conflicts;
        boolean onlyOwn = model.isOnlyCurrentUserSelected();
        User conflictUser = onlyOwn ? getUser() : null;
        conflicts= getConflicts( conflictUser);
        return conflicts;
    }
    
    private Collection<Conflict> getConflicts( User user) {

        List<Conflict> result = new ArrayList<Conflict>();
        for (Conflict conflict:conflicts) {
        	if (conflict.isOwner(user))
        	{
        		result.add(conflict);
        	}
        }
        Collections.sort( result, new ConflictStartDateComparator( ));
        return result;
    }
    
    class ConflictStartDateComparator implements Comparator<Conflict>
    {
        public int compare(Conflict c1, Conflict c2) {
           if ( c1.equals( c2))
           {
               return 0;
           }
           Date d1 = c1.getStartDate();
           Date d2 = c2.getStartDate();
           if ( d1 != null )
           {
               if ( d2 == null)
               {
                   return -1;
               }
               else
               {
                   int result = d1.compareTo( d2);
                   return result;
               }
           } 
           else if ( d2 !=  null)
           {
               return 1;
           }
           return new Integer(c1.hashCode()).compareTo( new Integer(c2.hashCode()));
        }
        
    }

    public void clearSelection() 
    {
        treeSelection.getTree().setSelectionPaths( new TreePath[] {});
    }

    public RaplaWidget<Component> getSummaryComponent() {
        return () -> summary;
    }   
    
   
}