package de.dreamlab.dash;


import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class DashLauncherAction extends AnAction {
    private final KeywordLookup androidKeywordLookup;
    private final KeywordLookup defaultKeywordLookup;

    private static final String XML_LANGUAGE_ID = "XML";
    private static final String ANDROID_SDK_ID = "Android SDK";

    public DashLauncherAction()
    {
        final DashLauncher launcher = new DashLauncher();
        androidKeywordLookup = new AndroidKeywordLookup(launcher);
        defaultKeywordLookup = new KeywordLookup(launcher);
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(PlatformDataKeys.EDITOR.getData(e.getDataContext()) != null);
    }

    public void actionPerformed(AnActionEvent e) {
        Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());

        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        PsiElement psiElement;
        Language language;

        if ( psiFile != null ) {
            psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
            language = elementLanguage(psiElement);
        }

        String query;

        SelectionModel selectionModel = editor.getSelectionModel();
        if ( selectionModel.hasSelection() ) {
            query = selectionModel.getSelectedText();
        }
        else {
            if ( psiElement == null || psiElement instanceof PsiComment ) {
                query = getWordAtCursor(editor);
            }
            else {
                query = psiElement.getText();
            }
        }

        if ( query != null ) {
            final Project project = e.getProject();
            if (project == null) {
                return;
            }

            KeywordLookup keywordLookup = defaultKeywordLookup;

            // Check if the current project is Java + Android, or otherwise.

            final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
            final Sdk sdk = projectRootManager.getProjectSdk();
            if (sdk != null) {
                final SdkTypeId sdkTypeId = sdk.getSdkType();
                if (ANDROID_SDK_ID.equals(sdkTypeId.getName())) {
                    keywordLookup = androidKeywordLookup;
                }
            }

            // show status message for potential troubleshooting
            String resolvedLanguage = keywordLookup.findLanguageName(language);

            String message;
            if ( resolvedLanguage == null ) {
                message = "Searching all docsets in Dash";
            }
            else {
                message = "Searching \"" + resolvedLanguage + "\" docsets in Dash";
            }

            if ( !language.getID().equals(resolvedLanguage) ) {
                message += ". Based on \"" + language.getID() + "\" context";
            }

            StatusBarUtil.setStatusBarInfo(project, message);

            // open dash
            keywordLookup.searchOnDash(language, query);
        }
    }

    private Language elementLanguage(PsiElement element)
    {
        if ( element == null ) {
            return null;
        }

        if ( XML_LANGUAGE_ID.equals(element.getLanguage().getID()) ) {
            PsiElement parent = element.getParent();

            final Language parentLanguage = parent.getLanguage();
            final Language baseLanguage = parentLanguage.getBaseLanguage();
            if ( baseLanguage == null || (!XML_LANGUAGE_ID.equals(parentLanguage.getID()) && XML_LANGUAGE_ID.equals(baseLanguage.getID())) ) {
                return parent.getLanguage();
            }
        }

        return element.getLanguage();
    }

    private String getWordAtCursor(Editor editor) {
        CharSequence editorText = editor.getDocument().getCharsSequence();
        int cursorOffset = editor.getCaretModel().getOffset();
        int editorTextLength = editorText.length();

        if ( editorTextLength == 0 ) {
            return null;
        }

        if ( (cursorOffset >= editorTextLength) || (cursorOffset > 1 && !isIdentifierPart(editorText.charAt(cursorOffset) ) && isIdentifierPart(editorText.charAt(cursorOffset - 1))) ) {
            cursorOffset--;
        }

        if ( isIdentifierPart(editorText.charAt(cursorOffset)) ) {
            int start = cursorOffset;
            int end = cursorOffset;

            while ( start > 0 && isIdentifierPart(editorText.charAt(start-1)) ) {
                start--;
            }

            while ( end < editorTextLength && isIdentifierPart(editorText.charAt(end)) ) {
                end++;
            }

            return editorText.subSequence(start, end).toString();
        }
        return null;
    }

    private boolean isIdentifierPart(char ch) {
        return Character.isJavaIdentifierPart(ch);
    }
}
