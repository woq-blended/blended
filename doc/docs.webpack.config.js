 merge = require("webpack-merge");
var config = require('./scalajs.webpack.config');
var ExtractTextPlugin = require("extract-text-webpack-plugin");
var path = require("path");

var rootDir = path.resolve(__dirname, "../../../../");

module.exports = merge(config, {
  entry: {
    'blended-bootstrap' : [ path.resolve(rootDir, 'scss/bootstrap/blended.scss') ]
  },
  plugins: [
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
