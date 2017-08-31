'use strict';

var path = require("path");

var webpack = require('webpack');
var CommonsChunkPlugin = webpack.optimize.CommonsChunkPlugin;


module.exports = {

  entry: {
    index: './bundles/index.js',
    mgmtUi: './target/blended-mgmt-ui-opt.js'
  },
  output: {
    path: path.resolve(__dirname, 'target/assets'),
    publicPath: "/assets/",
    filename: '[name]-bundle.js'
  },
  devServer: {
    port: 8090,
    clientLogLevel: "info",
    proxy: {
      "/management": {
        target: "http://localhost:8090",
        pathRewrite: {"^/management" : ""}
      }
    }
  },

  plugins: [
    new webpack.NoEmitOnErrorsPlugin(),
    new CommonsChunkPlugin({
      name: "index"
    })
  ],
  module: {
    rules: [
      {
        test: /\.css$/,
        use: [
          { loader: 'style-loader/url' },
          { loader: 'file-loader' }
        ]
      }
    ]
  }
};