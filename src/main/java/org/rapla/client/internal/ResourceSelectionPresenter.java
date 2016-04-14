/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.CalendarRefreshEvent;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuContext;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.FilterEditButton;
import org.rapla.client.swing.internal.FilterEditButton.FilterEditButtonFactory;
import org.rapla.client.swing.internal.MenuFactoryImpl;
import org.rapla.client.swing.internal.RaplaMenuBarContainer;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.action.RaplaObjectAction;
import org.rapla.client.swing.internal.edit.ClassifiableFilterEdit;
import org.rapla.client.swing.internal.view.TreeFactoryImpl;
import org.rapla.client.swing.toolkit.PopupEvent;
import org.rapla.client.swing.toolkit.PopupListener;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.client.swing.toolkit.RaplaWidget;
import org.rapla.components.calendar.RaplaArrowButton;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.PermissionController;

public class ResourceSelectionPresenter extends RaplaGUIComponent implements RaplaWidget
{
    protected JPanel content = new JPanel();
    public RaplaTree treeSelection = new RaplaTree();
    TableLayout tableLayout;
    protected JPanel buttonsPanel = new JPanel();

    protected final CalendarSelectionModel model;
    Listener listener = new Listener();

    protected FilterEditButton filterEdit;
    private final TreeFactory treeFactory;
    private final MenuFactory menuFactory;
    private final EditController editController;
    private final InfoFactory infoFactory;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaMenuBarContainer menuBar;
    private final EventBus eventBus;

    @Inject
    public ResourceSelectionPresenter(RaplaMenuBarContainer menuBar, ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            CalendarSelectionModel model, TreeFactory treeFactory, MenuFactory menuFactory, EditController editController, InfoFactory infoFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory, FilterEditButtonFactory filterEditButtonFactory,
            EventBus eventBus)
                    throws RaplaInitializationException
    {
        super(facade, i18n, raplaLocale, logger);

        this.menuBar = menuBar;
        this.model = model;
        this.eventBus = eventBus;
        this.treeFactory = treeFactory;
        this.menuFactory = menuFactory;
        this.editController = editController;
        this.infoFactory = infoFactory;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        /*double[][] sizes = new double[][] { { TableLayout.FILL }, { TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.FILL } };
        tableLayout = new TableLayout(sizes);*/
        content.setLayout(new BorderLayout());

        content.add(treeSelection);
        // content.setPreferredSize(new Dimension(260,400));
        content.setBorder(BorderFactory.createRaisedBevelBorder());

        content.add(buttonsPanel, BorderLayout.NORTH);

        buttonsPanel.setLayout(new BorderLayout());
        filterEdit = filterEditButtonFactory.create(model, true, listener);
        buttonsPanel.add(filterEdit.getButton(), BorderLayout.EAST);

        treeSelection.setToolTipRenderer(getTreeFactory().createTreeToolTipRenderer());
        treeSelection.setMultiSelect(true);
        treeSelection.getTree().setSelectionModel(((TreeFactoryImpl) getTreeFactory()).createComplexTreeSelectionModel());
        final TreeCellRenderer renderer = getTreeFactory().createRenderer();
        treeSelection.getTree().setCellRenderer(renderer);

        try
        {
            updateTree();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
        updateSelection();
        treeSelection.addChangeListener(listener);
        treeSelection.addPopupListener(listener);
        treeSelection.addDoubleclickListeners(listener);
        treeSelection.getTree().addFocusListener(listener);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(treeSelection.getTree());

        try
        {
            updateMenu();
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e);
        }
    }

    protected HashSet<?> setSelectedObjects()
    {
        HashSet<Object> elements = new HashSet<Object>(treeSelection.getSelectedElements());
        getModel().setSelectedObjects(elements);
        return elements;
    }

    public RaplaArrowButton getFilterButton()
    {
        return filterEdit.getButton();
    }

    public RaplaTree getTreeSelection()
    {
        return treeSelection;
    }

    public CalendarSelectionModel getModel()
    {
        return model;
    }

    public void dataChanged(ModificationEvent evt) throws RaplaException
    {
        if (evt != null && evt.isModified())
        {
            updateTree();
            updateMenu();
        }
        // No longer needed here as directly done in RaplaClientServiceImpl
        // ((CalendarModelImpl) model).dataChanged( evt);
    }

    final protected TreeFactory getTreeFactory()
    {
        return treeFactory;
    }

    boolean treeListenersEnabled = true;

    /*
     * (non-Javadoc)
     * 
     * @see org.rapla.client.swing.gui.internal.view.ITreeFactory#createClassifiableModel(org.rapla.entities.dynamictype.Classifiable[], org.rapla.entities.dynamictype.DynamicType[])
     */
    protected void updateTree() throws RaplaException
    {

        treeSelection.getTree().setRootVisible(false);
        final JTree tree = treeSelection.getTree();
        tree.setShowsRootHandles(true);
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON);
        tree.setDropTarget(new DropTarget(tree, TransferHandler.MOVE, new DropTargetAdapter()
        {
            private final Rectangle _raCueLine = new Rectangle();
            private final Color _colorCueLine = Color.blue;
            private TreePath lastPath = null;

            @Override
            public void dragOver(DropTargetDragEvent dtde)
            {
                TreePath selectionPath = tree.getSelectionPath();
                TreePath sourcePath = selectionPath.getParentPath();
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                Graphics2D g2 = (Graphics2D) tree.getGraphics();
                final Point dropLocation = dtde.getLocation();
                TreePath path = tree.getClosestPathForLocation(dropLocation.x, dropLocation.y);
                if(isDropAllowed(sourcePath, path, selectedNode))
                {
                    if (lastPath == null || !lastPath.equals(path))
                    {
                        if(lastPath != null)
                        {
                            drawLine(g2, lastPath, Color.white);
                        }
                        lastPath = path;
                        drawLine(g2, path, _colorCueLine);
                    }
                }
                else
                {
                    if(lastPath != null)
                    {
                        drawLine(g2, lastPath, Color.white);
                    }
                    lastPath = null;
                }
            }

            private void drawLine(Graphics2D g2, TreePath path, Color color)
            {
                Rectangle raPath = tree.getPathBounds(path);
                _raCueLine.setRect(0, raPath.y, tree.getWidth(), 2);
                g2.setColor(color);
                g2.fill(_raCueLine);
            }

            @Override
            public void dragEnter(DropTargetDragEvent dtde)
            {
                TreePath selectionPath = tree.getSelectionPath();
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                if (!(selectedNode.getUserObject() instanceof Category))
                {
                    dtde.rejectDrag();
                    return;
                }
                dtde.acceptDrag(DnDConstants.ACTION_MOVE);
            }

            @Override
            public void drop(DropTargetDropEvent dtde)
            {
                try
                {
                    TreePath selectionPath = tree.getSelectionPath();
                    TreePath sourcePath = selectionPath.getParentPath();
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                    Point dropLocation = dtde.getLocation();
                    TreePath targetPath = tree.getClosestPathForLocation(dropLocation.x, dropLocation.y);
                    if (isDropAllowed(sourcePath, targetPath, selectedNode))
                    {
                        DefaultMutableTreeNode targetParentNode = (DefaultMutableTreeNode) targetPath.getLastPathComponent();
                        final Category categoryToMove = (Category) selectedNode.getUserObject();
                        final Category targetCategory = (Category) targetParentNode.getUserObject();
                        Collection<Category> categoriesToStore = new ArrayList<>();
                        final Category categoryToMoveEdit = getFacade().edit(categoryToMove);
                        final Category targetParentCategoryEdit = getFacade().edit(targetCategory.getParent());
                        if (!targetParentCategoryEdit.hasCategory(categoryToMoveEdit))
                        {
                            // remove from old parent
                            final Category moveCategoryParent = getFacade().edit(categoryToMove.getParent());
                            moveCategoryParent.removeCategory(categoryToMoveEdit);
                            categoriesToStore.add(moveCategoryParent);
                        }
                        final Collection<Category> categories = getFacade().edit(Arrays.asList(targetParentCategoryEdit.getCategories()));
                        for (Category category : categories)
                        {
                            targetParentCategoryEdit.removeCategory(category);
                        }
                        for (Category category : categories)
                        {
                            if (category.equals(targetCategory))
                            {
                                targetParentCategoryEdit.addCategory(categoryToMoveEdit);
                            }
                            else if (category.equals(categoryToMoveEdit))
                            {
                                continue;
                            }
                            targetParentCategoryEdit.addCategory(category);
                        }
                        categoriesToStore.add(targetParentCategoryEdit);
                        categoriesToStore.add(categoryToMoveEdit);
                        try
                        {
                            getFacade().storeObjects(categoriesToStore.toArray(Entity.ENTITY_ARRAY));
                            dtde.dropComplete(true);
                            updateTree();
                        }
                        catch (Exception e)
                        {
                            dialogUiFactory.showError(e, new SwingPopupContext(getMainComponent(), null));
                            dtde.dropComplete(false);
                            updateTree();
                        }
                    }
                    else
                    {
                        dtde.rejectDrop();
                        dtde.dropComplete(false);
                    }
                }
                catch(RaplaException e)
                {
                    dtde.rejectDrop();
                    dtde.dropComplete(false);
                    getLogger().error("Error performing drag and drop operation: "+e.getMessage(), e);
                }
            }

            private boolean isDropAllowed(TreePath sourcePath, TreePath targetPath, DefaultMutableTreeNode selectedNode)
            {
                if (selectedNode.getUserObject() instanceof Category
                        && ((DefaultMutableTreeNode) targetPath.getLastPathComponent()).getUserObject() instanceof Category)
                {
                    Category targetCategory = (Category) ((DefaultMutableTreeNode) targetPath.getLastPathComponent()).getUserObject();
                    if(targetCategory.getId().equals(Category.SUPER_CATEGORY_REF.getId()))
                    {
                        return false;
                    }
                    return true;
                }
                return false;
            }

        }));
        DefaultTreeModel treeModel = generateTree();
        try
        {
            treeListenersEnabled = false;
            treeSelection.exchangeTreeModel(treeModel);
            updateSelection();
        }
        catch (Exception ex)
        {
            getLogger().error(ex.getMessage(), ex);
        }
        finally
        {
            treeListenersEnabled = true;
        }

    }

    protected DefaultTreeModel generateTree() throws RaplaException
    {
        ClassificationFilter[] filter = getModel().getAllocatableFilter();
        final TreeFactoryImpl treeFactoryImpl = (TreeFactoryImpl) getTreeFactory();
        DefaultTreeModel treeModel = treeFactoryImpl.createModel(filter);
        return treeModel;
    }

    protected void updateSelection()
    {
        Collection<Object> selectedObjects = new ArrayList<Object>(getModel().getSelectedObjects());
        treeSelection.select(selectedObjects);
    }

    public JComponent getComponent()
    {
        return content;
    }

    protected MenuContext createMenuContext(Point p, Object obj)
    {
        MenuContext menuContext = new MenuContext(obj, new SwingPopupContext(getComponent(), p));
        return menuContext;
    }

    protected void showTreePopup(PopupEvent evt)
    {
        try
        {

            Point p = evt.getPoint();
            Object obj = evt.getSelectedObject();
            List<?> list = treeSelection.getSelectedElements();

            MenuContext menuContext = createMenuContext(p, obj);
            menuContext.setSelectedObjects(list);

            RaplaPopupMenu menu = new RaplaPopupMenu();

            RaplaMenu newMenu = new RaplaMenu("new");
            newMenu.setText(getString("new"));
            boolean addNewReservationMenu = obj instanceof Allocatable || obj instanceof DynamicType;
            ((MenuFactoryImpl) getMenuFactory()).addNew(newMenu, menuContext, null, addNewReservationMenu);

            getMenuFactory().addObjectMenu(menu, menuContext, "EDIT_BEGIN");
            newMenu.setEnabled(newMenu.getMenuComponentCount() > 0);
            menu.insertAfterId(newMenu, "EDIT_BEGIN");

            JComponent component = (JComponent) evt.getSource();

            menu.show(component, p.x, p.y);
        }
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
        }
    }

    class Listener implements PopupListener, ChangeListener, ActionListener, FocusListener
    {

        public void showPopup(PopupEvent evt)
        {
            showTreePopup(evt);
        }

        public void actionPerformed(ActionEvent evt)
        {
            Object focusedObject = evt.getSource();
            if (focusedObject == null || !(focusedObject instanceof RaplaObject))
                return;
            // System.out.println(focusedObject.toString());
            Class type = ((RaplaObject) focusedObject).getTypeClass();
            if (type == User.class || type == Allocatable.class)
            {
                Entity entity = (Entity) focusedObject;

                RaplaObjectAction editAction = new RaplaObjectAction(getClientFacade(), getI18n(), getRaplaLocale(), getLogger(),
                        createPopupContext(getComponent(), null), editController, infoFactory, raplaImages, dialogUiFactory);
                PermissionController permissionController = getFacade().getPermissionController();
                try
                {
                    final User user = getUser();
                    if (permissionController.canModify(entity, user))
                    {
                        editAction.setEdit(entity);
                        editAction.actionPerformed();
                    }
                }
                catch (RaplaException e)
                {
                    getLogger().error("Error getting user in resource selection: "+e.getMessage(), e);
                }
            }
        }

        public void stateChanged(ChangeEvent evt)
        {
            if (!treeListenersEnabled)
            {
                return;
            }
            try
            {
                Object source = evt.getSource();
                ClassifiableFilterEdit filterUI = filterEdit.getFilterUI();
                if (filterUI != null && source == filterUI)
                {
                    final ClassificationFilter[] filters = filterUI.getFilters();
                    model.setAllocatableFilter(filters);
                    updateTree();
                    applyFilter();
                }
                else if (source == treeSelection)
                {
                    updateChange();
                }
            }
            catch (Exception ex)
            {
                dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
            }
        }

        public void focusGained(FocusEvent e)
        {
            try
            {
                if (!getUserModule().isSessionActive())
                {
                    return;
                }
                updateMenu();
            }
            catch (Exception ex)
            {
                dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
            }
        }

        public void focusLost(FocusEvent e)
        {
        }

    }

    public void updateChange() throws RaplaException
    {
        setSelectedObjects();
        updateMenu();
        applyFilter();
    }

    public void applyFilter() throws RaplaException
    {
        eventBus.fireEvent(new CalendarRefreshEvent());

    }

    public void updateMenu() throws RaplaException
    {
        RaplaMenu editMenu = menuBar.getEditMenu();
        RaplaMenu newMenu = menuBar.getNewMenu();

        editMenu.removeAllBetween("EDIT_BEGIN", "EDIT_END");
        newMenu.removeAll();

        List<?> list = treeSelection.getSelectedElements();
        Object focusedObject = null;
        if (list.size() == 1)
        {
            focusedObject = treeSelection.getSelectedElement();
        }

        MenuContext menuContext = createMenuContext(null, focusedObject);
        menuContext.setSelectedObjects(list);
        if (treeSelection.getTree().hasFocus())
        {
            getMenuFactory().addObjectMenu(editMenu, menuContext, "EDIT_BEGIN");
        }
        ((MenuFactoryImpl) getMenuFactory()).addNew(newMenu, menuContext, null, true);
        newMenu.setEnabled(newMenu.getMenuComponentCount() > 0);
    }

    public MenuFactory getMenuFactory()
    {
        return menuFactory;
    }


}