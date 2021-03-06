package OptimizePackage;

import MiddleDataUtilly.QT;
import java.util.ArrayList;
import org.jetbrains.annotations.Nullable;

/**
 * DAG优化算法类，只在package内可用
 */
class DAG {

    private ArrayList<QT> qts;

    private ArrayList<Node> nodes;

    DAG(ArrayList<QT> qts) {
        nodes = new ArrayList<>();
        this.qts = qts;
    }

    public ArrayList<QT> optimite() throws QtException{
        generateDAG();
        return generateQts();
    }

    /**
     *
     * 根据优化了的DAG图重组四元式
     */
    private ArrayList<QT> generateQts() {
        ArrayList<QT> result = new ArrayList<>();
        for (Node node : nodes) {
            if (node.getOperator() == null) {
                //如果没有操作符，就说明这是叶子节点
                for (String label : node.getExtra_labels()) {
                    if (!QT.isTemporaryVariable(label)) {
                        result.add(new QT("=", node.getMain_label(), null, label));
                    }
                }
            } else {
                //非叶节点
                Node left_child = node.getLeft_child();
                Node right_child = node.getRight_child();
                if (right_child == null) {
                    result.add(new QT(node.getOperator(), left_child.getMain_label(), null,
                        node.getMain_label()));
                } else {
                    result.add(new QT(node.getOperator(), left_child.getMain_label(),
                        right_child.getMain_label(), node.getMain_label()));
                }
                //把node中的附加标记处理
                for (String label : node.getExtra_labels()) {
                    if (!QT.isTemporaryVariable(label)) {
                        result.add(new QT("=", node.getMain_label(), null, label));
                    }
                }
            }
        }
        return result;
    }

    /**
     * 根据qts中的四元式序列生成DAG图
     * 存储在nodes中
     */
    private void generateDAG() throws QtException{
        for (QT qt : qts) {
            if (qt.getOperator().equals("=")) {
                //1. 若是赋值四元式: A=B  (=, B, _, A)
                // 查找之前定义的A（作为附加标号的）并删除， 如果是主标记，则不删除
                // 算法改进：赋值不能超越当前最新定义节点，如：a = 1；查找1的节点的时候如果碰到a的定义就不再继续查找
                ArrayList<Node> find_nodes = this.getAllDefineNodesAsExtraLabel(qt.getResult());
                for (Node node : find_nodes) {
                    node.getExtra_labels().remove(qt.getResult());
                }
                //算法改进:赋值不能超越当前最新定义节点，如：a = 1；查找1的节点的时候如果碰到a的定义就不再继续查找
                Node defined = this.getFirstDefindNode(qt.getOperand_left());
                Node newest_define = this.getFirstDefindNode(qt.getResult());
                if (defined == null || (newest_define != null && nodes.indexOf(newest_define) > nodes
                    .indexOf(defined))) {
                    //如果赋值右边没有定义
                    defined = new Node(qt.getOperand_left(), null, null,
                        null);
                    nodes.add(defined);
                }
                defined.addLabel(qt.getResult());

            } else if (QT.isConstVariable(qt.getOperand_left()) && (qt.getOperand_right() == null
                || QT.isConstVariable(qt.getOperand_right()))) {
                //如果是常值表达式
                // 查找之前定义的A（作为附加标号的）并删除， 如果是主标记，则不删除
                ArrayList<Node> find_nodes = this.getAllDefineNodesAsExtraLabel(qt.getResult());
                for (Node node : find_nodes) {
                    node.getExtra_labels().remove(qt.getResult());
                }
                //计算常值
                String const_result = this
                    .calculateConstExpression(qt.getOperator(), qt.getOperand_left(),
                        qt.getOperand_right());
                Node defined = this.getFirstDefindNode(const_result);
                if (defined == null) {
                    //如果该常数未定义
                    defined = new Node(const_result, null, null, null);
                    nodes.add(defined);
                }
                defined.addLabel(qt.getResult());

            } else {
                //查找之前定义的A（作为附加标号的）并删除， 如果是主标记，则不删除
                ArrayList<Node> find_nodes = this.getAllDefineNodesAsExtraLabel(qt.getResult());
                for (Node node : find_nodes) {
                    node.getExtra_labels().remove(qt.getResult());
                }
                Node public_expression = getExpression(qt);
                if (public_expression == null) {
                    Node left_node = null;
                    if (qt.getOperand_left() != null) {
                        left_node = getFirstDefindNode(qt.getOperand_left());
                        if (left_node == null) {
                            left_node = new Node(qt.getOperand_left(), null, null,
                                null);
                            nodes.add(left_node);
                        }
                    }
                    Node right_node = null;
                    if (qt.getOperand_right() != null) {
                        right_node = getFirstDefindNode(qt.getOperand_right());
                        if (right_node == null) {
                            right_node = new Node(qt.getOperand_right(), null, null,
                                null);
                            nodes.add(right_node);
                        }
                    }
                    Node new_node = new Node(qt.getResult(), qt.getOperator(), left_node,
                        right_node);
                    nodes.add(new_node);
                } else {
                    public_expression.addLabel(qt.getResult());
                }

            }
        }
    }

    /**
     * 获取最新定义这个标号的节点
     * 如果没找到就返回null
     *
     * @param label 标号
     * @return Node 最新定义这个标号的节点
     */
    @Nullable
    private Node getFirstDefindNode(String label) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node.containLabel(label)) {
                return node;
            }
        }
        return null;
    }

    /**
     * 获取定义这个标号的节点
     * 必须是这个节点的附加标号
     * 如果没有返回null
     *
     * @param label 标号
     * @return Node 在这里定义的节点
     */
    private ArrayList<Node> getAllDefineNodesAsExtraLabel(String label) {
        ArrayList<Node> r = new ArrayList<>();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node.containLabel(label) && !node.isMainLabel(label)) {
                r.add(node);
            }
        }
        return r;
    }

    /**
     * 获取公共表达式
     * 如果没找到返回null
     *
     * @return Node 表达式公共节点
     */
    @Nullable
    private Node getExpression(QT qt) {
        Node node;
        Node left_node = getFirstDefindNode(qt.getOperand_left());
        if (left_node == null) {
            return null;
        }
        Node right_node = getFirstDefindNode(qt.getOperand_right());
        if (right_node == null) {
            return null;
        }
        for (int i = nodes.size() - 1; i >= 0; i--) {
            node = nodes.get(i);
            if (node.getOperator() == null) {
                continue;
            }
            if (node.getLeft_child() == left_node && node.getRight_child() == right_node) {
                return node;
            }
        }
        return null;
    }

    /**
     * 计算常值表达式
     * operand_left + operand_right返回结果（常值标识符）
     *
     * @param operator 操作符
     * @param operand_left 操作数1
     * @param operand_right 操作数2
     * @return String 常值结果（标识符形式）
     */
    private String calculateConstExpression(String operator, String operand_left, String operand_right) throws QtException {
        if (operand_right == null) {
            return operand_left;
        }
        StringBuilder result = new StringBuilder("");
        String left_type = operand_left.split(" ")[1].split("_")[0];
        String right_type = operand_right.split(" ")[1].split("_")[0];
        if (left_type.equals("double") || right_type.equals("double")) {
            result.append("const double").append("_");
            double r = calculate(operator, operand_left, operand_right);
            result.append(Double.toString(r));
        } else if (left_type.equals("char") || right_type.equals("char")) {
            result.append("const char").append("_");
            double r = calculate(operator, operand_left, operand_right);
            result.append(Character.toString((char) r));
        } else if (left_type.equals("int") || right_type.equals("int")) {
            result.append("const int").append("_");
            double r = calculate(operator, operand_left, operand_right);
            result.append(Integer.toString((int) r));
        } else {
            throw new QtException("QtException: " + operand_left + " or " + operand_right
                + " is not a valid const\n");
        }
        return result.toString();
    }

    /**
     * 将常值标号转换成数字
     *
     * @param label 常值标号
     * @return 转换完成的数字
     */
    private double toNumber(String label) throws QtException{
        if (!QT.isConstVariable(label)) {
            throw new QtException(
                "QtException: " + label + "is not a const and can not be converted to number\n");
        }
        String[] split = label.split(" ")[1].split("_");
        if (split.length != 2) {
            throw new QtException("QtException: " + label + " is not a valid const\n");
        }
        try {
            switch (split[0]) {
                case "int":
                    return Integer.parseInt(split[1]);
                case "double":
                    return Double.parseDouble(split[1]);
                case "char":
                    if (split[1].length() != 1) {
                        throw new QtException(
                            "QtException const char error: " + label
                                + " can not be converted to character\n");
                    }
                    return split[1].charAt(0);
            }
        } catch (NumberFormatException e) {
            throw new QtException(
                "QtException const error: " + label
                    + " can not be converted to number\n");
        }
        throw new QtException("QtException: try to convert null to number\n");
    }

    /**
     * 计算，给入两个double数字与一个运算符（字符串）
     * 返回计算结果（double）
     *
     * @param operator 运算符
     * @param number1 操作数1
     * @param number2 操作数2
     * @return 运算结果
     */
    private double calculate(String operator, String number1, String number2) throws QtException{
        switch (operator) {
            case "+":
                return toNumber(number1) + toNumber(number2);
            case "-":
                return toNumber(number1) - toNumber(number2);
            case "*":
                return toNumber(number1) * toNumber(number2);
            case "/":
                return toNumber(number1) / toNumber(number2);
            case ">":
                if (toNumber(number1) > toNumber(number2)) {
                    return 1;
                } else {
                    return 0;
                }
            case ">=":
                if (toNumber(number1) >= toNumber(number2)) {
                    return 1;
                } else {
                    return 0;
                }
            case "<":
                if (toNumber(number1) < toNumber(number2)) {
                    return 1;
                } else {
                    return 0;
                }
            case "<=":
                if (toNumber(number1) <= toNumber(number2)) {
                    return 1;
                } else {
                    return 0;
                }
            case "==":
                if (toNumber(number1) == toNumber(number2)) {
                    return 1;
                } else {
                    return 0;
                }
            case "!=":
                if (toNumber(number1) != toNumber(number2)) {
                    return 1;
                } else {
                    return 0;
                }
            case "&&":
                if (toNumber(number1) != 0 && toNumber(number2) != 0) {
                    return 1;
                } else {
                    return 0;
                }
            case "||":
                if (toNumber(number1) == 0 && toNumber(number2) == 0) {
                    return 1;
                } else {
                    return 0;
                }
            case "!":
                if (number2 != null) {
                    throw new QtException("QtException: can to apply operator ! to two operands\n");
                }
                if (toNumber(number1) == 0) {
                    return 1;
                } else {
                    return 0;
                }
            default:
                throw new QtException("QtException: " + operator + " is not a valid operator\n");
        }
    }
}
