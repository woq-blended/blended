'use strict';

var path = require("path");
var webpack = require('webpack');

module.exports = {

  entry: {
    css: 'bootstrap-loader',
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
    new webpack.ProvidePlugin({
      $: 'jquery',
      jQuery: 'jquery'
    })
  ],
  devtool: "source-map",
  module: {
    rules: [{
      test: /\.scss$/,
      use: [{
        loader: "style-loader"
      }, {
        loader: "css-loader",
        options: {
          alias: {
            "../fonts/bootstrap": "bootstrap-sass/assets/fonts/bootstrap"
          }
        }
      }, {
        loader: "sass-loader",
        options: {
          includePaths: [
            path.resolve("./node_modules/bootstrap-sass/assets/stylesheets")
          ]
        }
      }]
    }, {
      test: /\.woff2?$|\.ttf$|\.eot$|\.svg$/,
      use: [{
        loader: "file-loader"
      }]
    }]
  }
};