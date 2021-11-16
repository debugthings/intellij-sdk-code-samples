// Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.sdk.codeInspection;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ImportsUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.Arrays;
import java.util.StringTokenizer;

import static com.siyeh.ig.psiutils.ExpressionUtils.isNullLiteral;

/**
 * Implements an inspection to detect when object references are compared using 'a==b' or 'a!=b'.
 * The quick fix converts these comparisons to 'a.equals(b) or '!a.equals(b)' respectively.
 */
public class ComparingReferencesInspection extends AbstractBaseJavaLocalInspectionTool {

    // Defines the text of the quick fix intention
    public static final String QUICK_FIX_NAME = "SDK: " +
            InspectionsBundle.message("inspection.comparing.references.use.quickfix");
    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ComparingReferencesInspection");
    private final CriQuickFix myQuickFix = new CriQuickFix();
    // This string holds a list of classes relevant to this inspection.
    @SuppressWarnings({"WeakerAccess"})
    @NonNls
    public String CHECKED_CLASSES = "java.lang.String;java.util.Date";

    /**
     * This method is called to get the panel describing the inspection.
     * It is called every time the user selects the inspection in preferences.
     * The user has the option to edit the list of {@link #CHECKED_CLASSES}.
     *
     * @return panel to display inspection information.
     */
    @Override
    public JComponent createOptionsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JTextField checkedClasses = new JTextField(CHECKED_CLASSES);
        checkedClasses.getDocument().addDocumentListener(new DocumentAdapter() {
            public void textChanged(@NotNull DocumentEvent event) {
                CHECKED_CLASSES = checkedClasses.getText();
            }
        });
        panel.add(checkedClasses);
        return panel;
    }

    /**
     * This method is overridden to provide a custom visitor.
     * that inspects expressions with relational operators '==' and '!='.
     * The visitor must not be recursive and must be thread-safe.
     *
     * @param holder     object for visitor to register problems found.
     * @param isOnTheFly true if inspection was run in non-batch mode
     * @return non-null visitor for this inspection.
     * @see JavaElementVisitor
     */
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {

            /**
             * This string defines the short message shown to a user signaling the inspection found a problem.
             * It reuses a string from the inspections bundle.
             */
            @NonNls
            private final String DESCRIPTION_TEMPLATE = "SDK " +
                    InspectionsBundle.message("inspection.comparing.references.problem.descriptor");

            /**
             * Avoid defining visitors for both Reference and Binary expressions.
             *
             * @param method The expression to be evaluated.
             */
            @Override
            public void visitMethod(PsiMethod method) {
                super.visitMethod(method);
                PsiAnnotation methodToCheck = method.getAnnotation("org.junit.Test");
                if (methodToCheck != null) {
                    boolean hasExpected = false;
                    for (PsiNameValuePair annotationParams : methodToCheck.getParameterList().getAttributes()) {
                        if (annotationParams.getNameIdentifier().getText().contains("expected")) {
                            hasExpected = true;
                            break;
                        }
                    }
                    if (hasExpected) {
                        holder.registerProblem(method,
                                DESCRIPTION_TEMPLATE, myQuickFix);
                    }
                }
            }

            /**
             * Evaluate binary psi expressions to see if they contain relational operators '==' and '!=', AND they contain
             * classes contained in CHECKED_CLASSES. The evaluation ignores expressions comparing an object to null.
             * IF this criteria is met, add the expression to the problems list.
             *
             * @param expression The binary expression to be evaluated.
             */
//            @Override
//            public void visitBinaryExpression(PsiBinaryExpression expression) {
//                super.visitBinaryExpression(expression);
//                IElementType opSign = expression.getOperationTokenType();
//                if (opSign == JavaTokenType.EQEQ || opSign == JavaTokenType.NE) {
//                    // The binary expression is the correct type for this inspection
//                    PsiExpression lOperand = expression.getLOperand();
//                    PsiExpression rOperand = expression.getROperand();
//                    if (rOperand == null || isNullLiteral(lOperand) || isNullLiteral(rOperand)) {
//                        return;
//                    }
//                    // Nothing is compared to null, now check the types being compared
//                    PsiType lType = lOperand.getType();
//                    PsiType rType = rOperand.getType();
//                    if (isCheckedType(lType) || isCheckedType(rType)) {
//                        // Identified an expression with potential problems, add to list with fix object.
//                        holder.registerProblem(expression,
//                                DESCRIPTION_TEMPLATE, myQuickFix);
//                    }
//                }
//            }

            /**
             * Verifies the input is the correct {@code PsiType} for this inspection.
             *
             * @param type The {@code PsiType} to be examined for a match
             * @return {@code true} if input is {@code PsiClassType} and matches one of the classes
             * in the {@link ComparingReferencesInspection#CHECKED_CLASSES} list.
             */
//            private boolean isCheckedType(PsiType type) {
//                if (!(type instanceof PsiClassType)) {
//                    return false;
//                }
//                StringTokenizer tokenizer = new StringTokenizer(CHECKED_CLASSES, ";");
//                while (tokenizer.hasMoreTokens()) {
//                    String className = tokenizer.nextToken();
//                    if (type.equalsToText(className)) {
//                        return true;
//                    }
//                }
//                return false;
//            }

        };
    }

    /**
     * This class provides a solution to inspection problem expressions by manipulating the PSI tree to use 'a.equals(b)'
     * instead of '==' or '!='.
     */
    private static class CriQuickFix implements LocalQuickFix {

        /**
         * Returns a partially localized string for the quick fix intention.
         * Used by the test code for this plugin.
         *
         * @return Quick fix short name.
         */
        @NotNull
        @Override
        public String getName() {
            return QUICK_FIX_NAME;
        }

        /**
         * This method manipulates the PSI tree to replace 'a==b' with 'a.equals(b)' or 'a!=b' with '!a.equals(b)'.
         *
         * @param project    The project that contains the file being edited.
         * @param descriptor A problem found by this inspection.
         */
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            try {
                // This should come in as a method
                PsiMethod testMethod = (PsiMethod) descriptor.getPsiElement();
                PsiFile file = testMethod.getContainingFile();

                PsiAnnotation psiAnnotation = testMethod.getAnnotation("org.junit.Test");

                // Get the factory for making new PsiElements, and the code style manager to format new statements
                final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
                final CodeStyleManager codeStylist = CodeStyleManager.getInstance(project);
                boolean canImport = ImportUtils.addStaticImport("org.junit.jupiter.api.Assertions", "assertThrows", testMethod);


                if (psiAnnotation == null && !psiAnnotation.getText().contains("expected")) {
                    return;
                }

                PsiJavaCodeReferenceElement psiJavaCodeReferenceElement = psiAnnotation.getNameReferenceElement();
                boolean className = psiAnnotation.getText().contains("Test");
                if (className) {
                    PsiCodeBlock codeBlock = testMethod.getBody();
                    PsiAnnotationParameterList parameterList = psiAnnotation.getParameterList();
                    PsiClassObjectAccessExpression exceptionToGet = null;
                    PsiElement removeThisAttribute = null;

                    // Search the annotation for the "expected" parameter
                    for (PsiNameValuePair parameterElement : parameterList.getAttributes()) {
                        PsiNameValuePair annotationParameter = (PsiNameValuePair) parameterElement;
                        if (annotationParameter.getNameIdentifier().getText().contains("expected")) {
                            exceptionToGet = (PsiClassObjectAccessExpression) annotationParameter.getValue();
                            removeThisAttribute = parameterElement;
                        }
                    }

                    if (removeThisAttribute != null) {
                        removeThisAttribute.delete();
                        if (parameterList.getAttributes().length == 0) {
                            parameterList.delete();
                        }
                    }

                    // Generate the assertThrows statement from the existing code block and create a new codeblock to replace
                    PsiStatement psiExpressionStatement = factory.createStatementFromText("assertThrows(" + exceptionToGet.getText() + ", () -> {});", null);
                    PsiMethodCallExpression assertMethodCallExpression = (PsiMethodCallExpression) psiExpressionStatement.getFirstChild();
                    PsiLambdaExpression expressionList = (PsiLambdaExpression) assertMethodCallExpression.getArgumentList().getExpressions()[1];
                    expressionList.getLastChild().replace(codeBlock.copy());
                    PsiCodeBlock lambdaCodeBlock = factory.createCodeBlock();
                    lambdaCodeBlock.add(psiExpressionStatement);
                    codeBlock.replace(lambdaCodeBlock);
                }
            } catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }

        @NotNull
        public String getFamilyName() {
            return getName();
        }

    }

}
