merge = require("webpack-merge");
var config = require('./scalajs.webpack.config');
var ExtractTextPlugin = require("extract-text-webpack-plugin");
var path = require("path");

// out pwd is `target/scala_2.12/scalajs-bundler/node_modules`
// rootDir should be the project base dir
var rootDir = path.resolve(__dirname, "../../../../");

module.exports = merge(config, {
  entry: {
    'blended-bootstrap' : [ path.resolve(rootDir, 'scss/bootstrap/blended.scss') ]
  },
  plugins: [
    // convert *.scss files to *.css
    new ExtractTextPlugin("[name].css")
  ],
  module: {
    rules: [{
      test: /\.scss$/,
      use: ExtractTextPlugin.extract({
        fallback: 'style-loader',
        use: [
          'raw-loader',
          'sass-loader'
        ]
      })
    }]
  }
});
