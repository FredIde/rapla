package org.rapla.client.gwt;

import org.rapla.client.gwt.dagger.DaggerRaplaGwtComponent;
import org.rapla.client.gwt.view.RaplaPopups;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

public class Rapla implements EntryPoint
{

    public void onModuleLoad()
    {
        RaplaPopups.getProgressBar().setPercent(10);
        GwtStarter starter = DaggerRaplaGwtComponent.create().getGwtStarter();//GWT.create(GwtStarter.class);
        starter.startApplication();
    }

}