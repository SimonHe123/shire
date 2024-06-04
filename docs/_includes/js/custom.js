function shire_lang(hljs) {
  let regex = hljs.regex
  const DATE_RE = '[0-9]{4}(-[0-9][0-9]){0,2}';
  const TIME_RE = '([Tt \\t][0-9][0-9]?(:[0-9][0-9]){2})?';
  const FRACTION_RE = '(\\.[0-9]*)?';
  const ZONE_RE = '([ \\t])*(Z|[-+][0-9][0-9]?(:[0-9][0-9])?)?';
  const TIMESTAMP = {
    className: 'number',
    begin: '\\b' + DATE_RE + TIME_RE + FRACTION_RE + ZONE_RE + '\\b'
  };

  let FRONTMATTER = {
    className: 'meta',
    begin: '^---$',
    end: '^---$',
    contains: [
      TIMESTAMP,
      {
        className: 'string',
        begin: '"', end: '"'
      },
      {
        className: 'string',
        begin: "'", end: "'"
      },
      {
        className: 'number',
        begin: '\\d+(\\.\\d+)?'
      },
      {
        className: 'attr',
        variants: [
          // added brackets support
          {
            begin: /\w[\w :()\./-]*:(?=[ \t]|$)/
          },
          { // double quoted keys - with brackets
            begin: /"\w[\w :()\./-]*":(?=[ \t]|$)/
          },
          { // single quoted keys - with brackets
            begin: /'\w[\w :()\./-]*':(?=[ \t]|$)/
          },
        ]
      }
    ]
  }

  let INLINE_HTML = {
    begin: /<\/?[A-Za-z_]/,
    end: ">",
    subLanguage: "xml",
    relevance: 0,
  }
  let HORIZONTAL_RULE = {
    begin: "^[-\\*]{3,}",
    end: "$",
  }
  let CODE = {
    className: "code",
    variants: [
      // TODO: fix to allow these to work with sublanguage also
      { begin: "(`{3,})[^`](.|\\n)*?\\1`*[ ]*" },
      { begin: "(~{3,})[^~](.|\\n)*?\\1~*[ ]*" },
      // needed to allow markdown as a sublanguage to work
      {
        begin: "```",
        end: "```+[ ]*$",
      },
      {
        begin: "~~~",
        end: "~~~+[ ]*$",
      },
      { begin: "`.+?`" },
      {
        begin: "(?=^( {4}|\\t))",
        // use contains to gobble up multiple lines to allow the block to be whatever size
        // but only have a single open/close tag vs one per line
        contains: [
          {
            begin: "^( {4}|\\t)",
            end: "(\\n)$",
          },
        ],
        relevance: 0,
      },
    ],
  }
  let LIST = {
    className: "bullet",
    begin: "^[ \t]*([*+-]|(\\d+\\.))(?=\\s+)",
    end: "\\s+",
    excludeEnd: true,
  }
  let LINK_REFERENCE = {
    begin: /^\[[^\n]+\]:/,
    returnBegin: true,
    contains: [
      {
        className: "symbol",
        begin: /\[/,
        end: /\]/,
        excludeBegin: true,
        excludeEnd: true,
      },
      {
        className: "link",
        begin: /:\s*/,
        end: /$/,
        excludeBegin: true,
      },
    ],
  }
  let URL_SCHEME = /[A-Za-z][A-Za-z0-9+.-]*/
  let LINK = {
    variants: [
      // too much like nested array access in so many languages
      // to have any real relevance
      {
        begin: /\[.+?\]\[.*?\]/,
        relevance: 0,
      },
      // popular internet URLs
      {
        begin: /\[.+?\]\(((data|javascript|mailto):|(?:http|ftp)s?:\/\/).*?\)/,
        relevance: 2,
      },
      {
        begin: regex.concat(/\[.+?\]\(/, URL_SCHEME, /:\/\/.*?\)/),
        relevance: 2,
      },
      // relative urls
      {
        begin: /\[.+?\]\([./?&#].*?\)/,
        relevance: 1,
      },
      // whatever else, lower relevance (might not be a link at all)
      {
        begin: /\[.*?\]\(.*?\)/,
        relevance: 0,
      },
    ],
    returnBegin: true,
    contains: [
      {
        // empty strings for alt or link text
        match: /\[(?=\])/,
      },
      {
        className: "string",
        relevance: 0,
        begin: "\\[",
        end: "\\]",
        excludeBegin: true,
        returnEnd: true,
      },
      {
        className: "link",
        relevance: 0,
        begin: "\\]\\(",
        end: "\\)",
        excludeBegin: true,
        excludeEnd: true,
      },
      {
        className: "symbol",
        relevance: 0,
        begin: "\\]\\[",
        end: "\\]",
        excludeBegin: true,
        excludeEnd: true,
      },
    ],
  }
  let BOLD = {
    className: "strong",
    contains: [], // defined later
    variants: [
      {
        begin: /_{2}(?!\s)/,
        end: /_{2}/,
      },
      {
        begin: /\*{2}(?!\s)/,
        end: /\*{2}/,
      },
    ],
  }
  let ITALIC = {
    className: "emphasis",
    contains: [], // defined later
    variants: [
      {
        begin: /\*(?![*\s])/,
        end: /\*/,
      },
      {
        begin: /_(?![_\s])/,
        end: /_/,
        relevance: 0,
      },
    ],
  }

  // 3 level deep nesting is not allowed because it would create confusion
  // in cases like `***testing***` because where we don't know if the last
  // `***` is starting a new bold/italic or finishing the last one
  let BOLD_WITHOUT_ITALIC = hljs.inherit(BOLD, { contains: [] })
  let ITALIC_WITHOUT_BOLD = hljs.inherit(ITALIC, { contains: [] })
  BOLD.contains.push(ITALIC_WITHOUT_BOLD)
  ITALIC.contains.push(BOLD_WITHOUT_ITALIC)

  let CONTAINABLE = [
    INLINE_HTML,
    LINK,
  ];

  [
    BOLD,
    ITALIC,
    BOLD_WITHOUT_ITALIC,
    ITALIC_WITHOUT_BOLD,
  ].forEach(m => {
    m.contains = m.contains.concat(CONTAINABLE)
  })

  CONTAINABLE = CONTAINABLE.concat(BOLD, ITALIC)

  let HEADER = {
    className: "section",
    variants: [
      {
        begin: "^#{1,6}",
        end: "$",
        contains: CONTAINABLE,
      },
      {
        begin: "(?=^.+?\\n[=-]{2,}$)",
        contains: [
          { begin: "^[=-]*$" },
          {
            begin: "^",
            end: "\\n",
            contains: CONTAINABLE,
          },
        ],
      },
    ],
  }

  let BLOCKQUOTE = {
    className: "quote",
    begin: "^>\\s+",
    contains: CONTAINABLE,
    end: "$",
  }

  let ENTITY = {
    //https://spec.commonmark.org/0.31.2/#entity-references
    scope: "literal",
    match: /&([a-zA-Z0-9]+|#[0-9]{1,7}|#[Xx][0-9a-fA-F]{1,6});/,
  }

  let KEYWORDS = [
    "file",
    "rev",
    "refactor",
    "symbol",
    "write",
    "run",
    "commit",
    "file-func",
    "browse",
    "refactor",
  ]

  return {
    name: "shire",
    aliases: [
      "sre",
      "shire",
    ],
    keywords: {
      keyword: KEYWORDS,
    },
    contains: [
      FRONTMATTER,
      HEADER,
      INLINE_HTML,
      LIST,
      BOLD,
      ITALIC,
      BLOCKQUOTE,
      CODE,
      HORIZONTAL_RULE,
      LINK,
      LINK_REFERENCE,
      ENTITY,
    ],
  }
}

document.addEventListener("DOMContentLoaded", (event) => {
  document.querySelectorAll("pre code").forEach((el) => {
    let langs = hljs.listLanguages()
    if (!langs.includes("shire")) {
      hljs.registerLanguage("shire", function() {
        return shire_lang(hljs)
      })
      hljs.highlightAll()
    }
  })
})
