/*
 * To change this license header, choose License Headers in Project Properties.
 *
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.core.web.fs.files;

import net.vpc.app.vainruling.core.service.CorePlugin;
import net.vpc.app.vainruling.core.service.VrApp;
import net.vpc.app.vainruling.core.service.security.UserSession;
import net.vpc.app.vainruling.core.service.util.VrUtils;
import net.vpc.app.vainruling.core.web.Vr;
import net.vpc.app.vainruling.core.web.VrController;
import net.vpc.app.vainruling.core.web.obj.DialogResult;
import net.vpc.app.vainruling.core.web.util.FileUploadEventHandler;
import net.vpc.common.jsf.FacesUtils;
import net.vpc.common.strings.StringUtils;
import net.vpc.common.vfs.VFile;
import net.vpc.common.vfs.VFileType;
import net.vpc.common.vfs.VirtualFileSystem;
import net.vpc.upa.UPA;
import net.vpc.upa.VoidAction;
import org.primefaces.context.RequestContext;
import org.primefaces.event.FileUploadEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author taha.bensalah@gmail.com
 */
@VrController
public class DocumentsUploadDialogCtrl {

    private static final Logger log = Logger.getLogger(DocumentsUploadDialogCtrl.class.getName());

    private Model model = new Model();

    public void openDialog(String config) {
        openDialog(VrUtils.parseJSONObject(config, Config.class));
    }


    public void openCustomDialog(Config config,DocumentUploadListener listener) {
        getModel().setListener(listener);
        initContent(config);

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("resizable", false);
        options.put("draggable", true);
        options.put("modal", true);

        RequestContext.getCurrentInstance().openDialog("/modules/files/documents-upload-dialog", options, null);
    }

    public void openDialog(Config config) {
        openCustomDialog(config,null);
    }

    public void fireEventExtraDialogClosed() {
        RequestContext.getCurrentInstance().closeDialog(
                new DialogResult(null, getModel().getConfig().getUserInfo())
        );
    }

    public void initContent(Config cmd) {
        if(cmd==null){
            cmd=new Config();
        }
        if(cmd.getSizeLimit()<=0){
            cmd.setSizeLimit(1024*1024*30);
        }
        getModel().setConfig(cmd);
        Config c = getModel().getConfig();
        if (c == null) {
            c = new Config();
        }
        String title = c.getTitle();
        if (StringUtils.isEmpty(title)) {
            title = "Documents";
        }
        getModel().setTitle(title);
    }

    public void handleNewFile(FileUploadEvent event) {
        if(getModel().getListener()!=null){
            RequestContext.getCurrentInstance().closeDialog(
                    new DialogResult(null, getModel().getConfig().getUserInfo())
            );
            getModel().getListener().onUpload(event);
            return;
        }
        UPA.getContext().invokePrivileged(new VoidAction() {
            @Override
            public void run() {
                try {
                    String fspath = getModel().getConfig().getFspath();
                    if(StringUtils.isEmpty(fspath)){
                        fspath="/Upload/";
                    }
                    if(!fspath.startsWith("/")){
                        fspath=fspath="/"+fspath;
                    }
                    CorePlugin fsp = VrApp.getBean(CorePlugin.class);
                    VFile ufs = fsp.getUserFolder(fsp.getCurrentUserLogin());
                    VFile folder = ufs.get("/Upload");
                    folder.mkdirs();
                    VFile file=CorePlugin.get().uploadFile(folder, new FileUploadEventHandler(event) );
                    String baseFile = file.getBaseFile("vrfs").getPath();
                    RequestContext.getCurrentInstance().closeDialog(
                            new DialogResult(baseFile, getModel().getConfig().getUserInfo())
                    );
                    FacesUtils.addInfoMessage(event.getFile().getFileName() + " successfully uploaded.");
                } catch (Exception ex) {
                    Logger.getLogger(DocumentsCtrl.class.getName()).log(Level.SEVERE, null, ex);
                    FacesUtils.addErrorMessage(ex,event.getFile().getFileName() + " uploading failed.");
                }
            }
        });
    }

    public Model getModel() {
        return model;
    }

    public static class Config {

        private String type;
        private String value;
        private String title;
        private String path;
        private String userInfo;
        private String fspath;
        private long sizeLimit;
        private String extensions;

        public String getType() {
            return type;
        }

        public Config setType(String type) {
            this.type = type;
            return this;
        }

        public String getValue() {
            return value;
        }

        public Config setValue(String value) {
            this.value = value;
            return this;
        }

        public String getTitle() {
            return title;
        }

        public Config setTitle(String title) {
            this.title = title;
            return this;
        }

        public String getPath() {
            return path;
        }

        public Config setPath(String path) {
            this.path = path;
            return this;
        }

        public String getUserInfo() {
            return userInfo;
        }

        public Config setUserInfo(String userInfo) {
            this.userInfo = userInfo;
            return this;
        }

        public String getFspath() {
            return fspath;
        }

        public Config setFspath(String fspath) {
            this.fspath = fspath;
            return this;
        }

        public long getSizeLimit() {
            return sizeLimit;
        }

        public Config setSizeLimit(long sizeLimit) {
            this.sizeLimit = sizeLimit;
            return this;
        }

        public String getExtensions() {
            return extensions;
        }

        public Config setExtensions(String extensions) {
            this.extensions = extensions;
            return this;
        }
    }

    public static class Model {

        private String title;
        private String current;
        private Config config;
        private DocumentUploadListener listener;

        public DocumentUploadListener getListener() {
            return listener;
        }

        public void setListener(DocumentUploadListener listener) {
            this.listener = listener;
        }

        public String getCurrent() {
            return current;
        }

        public void setCurrent(String current) {
            this.current = current;
        }

        public Config getConfig() {
            return config;
        }

        public void setConfig(Config config) {
            this.config = config;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }


    }

}
