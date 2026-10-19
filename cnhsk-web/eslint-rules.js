const CLASS_HELPER_NAMES = new Set(['cn', 'clsx', 'twMerge'])
const HEX_COLOR_PATTERN = /#[0-9a-fA-F]{3,8}/
const MOCKUP_CLASS_PATTERN = /(?:^|[\s:])(?:c|u|t)-[A-Za-z]/

function getStringSegments(node) {
  if (!node) {
    return []
  }

  if (node.type === 'Literal' && typeof node.value === 'string') {
    return [node.value]
  }

  if (node.type === 'TemplateLiteral') {
    return [
      ...node.quasis.map((quasi) => quasi.value.raw),
      ...node.expressions.flatMap(getStringSegments),
    ]
  }

  if (node.type === 'ConditionalExpression') {
    return [...getStringSegments(node.consequent), ...getStringSegments(node.alternate)]
  }

  if (node.type === 'LogicalExpression') {
    return [...getStringSegments(node.left), ...getStringSegments(node.right)]
  }

  if (node.type === 'ArrayExpression') {
    return node.elements.flatMap((element) =>
      element?.type === 'SpreadElement' ? [] : getStringSegments(element),
    )
  }

  return []
}

function getClassSegmentsFromAttribute(node) {
  if (node.name.name !== 'className' || !node.value) {
    return []
  }

  if (node.value.type === 'JSXExpressionContainer') {
    return getStringSegments(node.value.expression)
  }

  return getStringSegments(node.value)
}

function getClassSegmentsFromCall(node) {
  if (node.callee.type !== 'Identifier' || !CLASS_HELPER_NAMES.has(node.callee.name)) {
    return []
  }

  return node.arguments.flatMap((argument) =>
    argument.type === 'SpreadElement' ? [] : getStringSegments(argument),
  )
}

function createClassRule({ message, pattern }) {
  return {
    meta: {
      type: 'problem',
      docs: {
        description: message,
      },
      schema: [],
      messages: {
        forbiddenClass: message,
      },
    },
    create(context) {
      function checkSegments(node, segments) {
        if (segments.some((segment) => pattern.test(segment))) {
          context.report({ node, messageId: 'forbiddenClass' })
        }
      }

      return {
        JSXAttribute(node) {
          checkSegments(node, getClassSegmentsFromAttribute(node))
        },
        CallExpression(node) {
          checkSegments(node, getClassSegmentsFromCall(node))
        },
      }
    },
  }
}

const cnhskPlugin = {
  rules: {
    'no-hex-color-class': createClassRule({
      message: 'Không dùng mã màu hex trong class; hãy dùng semantic token Tailwind.',
      pattern: HEX_COLOR_PATTERN,
    }),
    'no-mockup-class': createClassRule({
      message: 'Không dùng class c-*, u-* hoặc t-* từ mockup HTML.',
      pattern: MOCKUP_CLASS_PATTERN,
    }),
  },
}

export default cnhskPlugin
