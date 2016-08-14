/*
 * To change this license header, choose License Headers in Project Properties.
 *
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.plugins.articles.web;

import net.vpc.app.vainruling.core.service.VrApp;
import net.vpc.app.vainruling.core.service.model.AppUser;
import net.vpc.app.vainruling.core.service.security.UserSession;
import net.vpc.app.vainruling.plugins.articles.service.ArticlesPlugin;
import net.vpc.app.vainruling.plugins.articles.service.model.ArticlesFile;
import net.vpc.app.vainruling.plugins.articles.service.model.ArticlesItem;
import net.vpc.app.vainruling.plugins.articles.service.model.FullArticle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author taha.bensalah@gmail.com
 */
@Controller
@Scope(value = "session")
public class ArticlesCtrl {

    @Autowired
    private ArticlesPlugin articles;
    private Model model = new Model();
    private String[] imageSwitchEffects = new String[]{
            "blindX",
            "blindY",
            "blindZ",
            "cover",
            "curtainX",
            "curtainY",
            "fade",
            "fadeZoom",
            "growX",
            "growY",
            "none",
            "scrollUp",
            "scrollDown",
            "scrollLeft",
            "scrollRight",
            "scrollVert",
            "shuffle",
            "slideX",
            "slideY",
            "toss",
            "turnUp",
            "turnDown",
            "turnLeft",
            "turnRight",
            "uncover",
            "wipe",
            "zoom"
    };

    public Model getModel() {
        return model;
    }

    public void refesh() {
    }

    public void loadArticles(String name) {
        List<FullArticle> a = findArticles(name);
        getModel().getArticles().put(name, a);
        if (a != null && a.size() > 0) {
            getModel().setCurrent(a.get(0));
        } else {
            getModel().setCurrent(null);
        }
    }

    public List<FullArticle> getMainRow1Articles() {
        return findArticles("Main.Row1");
    }

//    public List<FullArticle> getWelcomeArticles() {
//        getModel().getArticles().put("Welcome",findArticles("Welcome"))
//        return findArticles("Welcome");
//    }

    public List<FullArticle> getMainRow2Articles() {
        return findArticles("Main.Row2");
    }

    public List<FullArticle> getMainRow3Articles() {
        return findArticles("Main.Row3");
    }

    public List<FullArticle> getActivities() {
        return findArticles("Activities");
    }

    public String getImageSwitchRandomEffect() {
        return imageSwitchEffects[(int) (Math.random() * imageSwitchEffects.length)];
    }

    public List<FullArticle> getMainRow4Articles() {
        return findArticles("Main.Row4");
    }

    public List<FullArticle> getMainRow5Articles() {
        return findArticles("Main.Row5");
    }

    public List<FullArticle> getMainRow6Articles() {
        return findArticles("Main.Row6");
    }

    public List<FullArticle> getMainRow7Articles() {
        return findArticles("Main.Row7");
    }

    public List<FullArticle> findArticles(String disposition) {
        AppUser u = UserSession.getCurrentUser();
        return articles.findFullArticlesByUserAndCategory(u == null ? null : u.getLogin(), disposition);
    }

    public List<ArticlesFile> findArticlesFiles(int articleId) {
        return articles.findArticlesFiles(articleId);
    }

    public FullArticle getFullArticle(String disposition,int pos){
        List<FullArticle> a = getModel().getArticles().get(disposition);
        if(a!=null&& a.size()>pos && pos>=0){
            return a.get(pos);
        }
        return null;
    }

    public ArticlesItem getArticle(String disposition, int pos){
        FullArticle a = getFullArticle(disposition, pos);
        if(a!=null){
            return a.getContent();
        }
        return null;
    }

    public static class Model {

        private FullArticle current;
        private Map<String, List<FullArticle>> articles = new HashMap<>();

        public FullArticle getCurrent() {
            return current;
        }

        public void setCurrent(FullArticle current) {
            this.current = current;
        }

        public Map<String, List<FullArticle>> getArticles() {
            return articles;
        }

        public void setArticles(Map<String, List<FullArticle>> articles) {
            this.articles = articles;
        }
    }
}
